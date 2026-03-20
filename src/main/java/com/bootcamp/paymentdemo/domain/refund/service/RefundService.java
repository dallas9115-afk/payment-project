package com.bootcamp.paymentdemo.domain.refund.service;

import com.bootcamp.paymentdemo.domain.payment.entity.Payment;
import com.bootcamp.paymentdemo.domain.payment.service.PaymentAccessValidator;
import com.bootcamp.paymentdemo.domain.payment.service.PaymentLifecycleService;
import com.bootcamp.paymentdemo.domain.refund.dto.Request.RefundRequest;
import com.bootcamp.paymentdemo.domain.refund.dto.Response.RefundResponse;
import com.bootcamp.paymentdemo.domain.refund.dto.Response.RefundSummaryResponse;
import com.bootcamp.paymentdemo.domain.refund.entity.Refund;
import com.bootcamp.paymentdemo.domain.refund.enums.CancelFlow;
import com.bootcamp.paymentdemo.domain.refund.enums.RefundStatus;
import com.bootcamp.paymentdemo.domain.refund.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRepository refundRepository;
    private final PaymentLifecycleService paymentLifecycleService;
    private final PaymentAccessValidator paymentAccessValidator;

    @Transactional
    public RefundResponse cancel(Authentication authentication, String paymentId, RefundRequest request) {
        Payment payment = paymentAccessValidator.getAuthorizedPayment(authentication, paymentId); // 인증인가검증

        Refund existingRefund = refundRepository.findByPayment(payment).orElse(null); //환불 객체가있는지확인
        if (existingRefund != null && existingRefund.getStatus() == RefundStatus.REFUNDED) {  // 멱등처리
            return RefundResponse.alreadyRefunded(existingRefund);
        }

        if (!payment.isRefundable()) {  //멱등처리
            throw new IllegalArgumentException("환불은 결제완료상태만 가능합니다.");
        }

        String reason = request.reason();
        Refund refund = existingRefund;
        if (refund == null) {  // 환불 객체생성
            refund = Refund.createRequested(payment, payment.getAmount(), reason);
            refundRepository.save(refund);
        }

        String resultMessage = paymentLifecycleService.cancelApprovedPayment( //환불로직 진행
                paymentId,
                reason,
                CancelFlow.REFUND
        );

        Refund updatedRefund = refundRepository.findByPayment(payment).orElseThrow(
                () -> new IllegalStateException("환불 처리 후 환불 레코드를 찾을 수 없습니다. paymentId=" + paymentId)
        );

        if (updatedRefund.getStatus() == RefundStatus.REFUNDED) {
            return RefundResponse.success(updatedRefund, resultMessage);
        }

        return RefundResponse.failed(updatedRefund, resultMessage);
    }

    public RefundSummaryResponse getRefund(Authentication authentication, String paymentId) {
        Payment payment = paymentAccessValidator.getAuthorizedPayment(authentication, paymentId);
        Refund refund = refundRepository.findByPayment(payment).orElseThrow(
                () -> new IllegalStateException("결제 시도 내역이 없습니다. paymentId=" + paymentId)
        );
        return RefundSummaryResponse.from(refund);



    }
}
