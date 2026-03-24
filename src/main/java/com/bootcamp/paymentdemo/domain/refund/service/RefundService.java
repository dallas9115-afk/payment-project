package com.bootcamp.paymentdemo.domain.refund.service;

import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.payment.entity.Payment;
import com.bootcamp.paymentdemo.domain.payment.service.PaymentAccessValidator;
import com.bootcamp.paymentdemo.domain.payment.service.PaymentLifecycleService;
import com.bootcamp.paymentdemo.domain.point.service.PointRefundPreview;
import com.bootcamp.paymentdemo.domain.point.service.PointTransactionService;
import com.bootcamp.paymentdemo.domain.refund.dto.RefundCalculation;
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
    private final PointTransactionService pointTransactionService;

    @Transactional
    public RefundResponse cancel(Authentication authentication, String paymentId, RefundRequest request) {
        Payment payment = paymentAccessValidator.getAuthorizedPayment(authentication, paymentId);
        Customer customer = paymentAccessValidator.getAuthenticatedCustomer(authentication);
        Refund existingRefund = refundRepository.findByPayment(payment).orElse(null);
        if (existingRefund != null && existingRefund.getStatus() == RefundStatus.REFUNDED) {
            return RefundResponse.alreadyRefunded(existingRefund);
        }

        if (!payment.isRefundable()) {
            throw new IllegalArgumentException("환불은 결제완료상태만 가능합니다.");
        }

        // 환불 시작 시 포인트 영향을 한 번만 계산하고, 이 값을 취소/재시도까지 그대로 넘긴다.
        RefundCalculation refundCalculation = calculateRefund(payment, customer.getId());
        Long cancelAmount = refundCalculation.cancelAmount();

        String reason = request.reason();
        Refund refund = existingRefund;
        if (refund == null) {
            refund = Refund.createRequested(payment, cancelAmount, reason);
            refundRepository.save(refund);
        }

        String resultMessage = paymentLifecycleService.cancelApprovedPayment(
                paymentId,
                reason,
                CancelFlow.REFUND,
                refundCalculation
        );

        Refund updatedRefund = refundRepository.findByPayment(payment).orElseThrow(
                () -> new IllegalStateException("환불 처리 후 환불 레코드를 찾을 수 없습니다. paymentId=" + paymentId)
        );

        if (updatedRefund.getStatus() == RefundStatus.REFUNDED) {
            return RefundResponse.success(updatedRefund, resultMessage);
        }

        return RefundResponse.failed(updatedRefund, resultMessage);
    }

    // 단건 환불 이력 조회
    public RefundSummaryResponse getRefund(Authentication authentication, String paymentId) {
        Payment payment = paymentAccessValidator.getAuthorizedPayment(authentication, paymentId);
        Refund refund = refundRepository.findByPayment(payment).orElseThrow(
                () -> new IllegalStateException("결제 시도 내역이 없습니다. paymentId=" + paymentId)
        );
        return RefundSummaryResponse.from(refund);

    }

    // 환불 금액과 적립 포인트 회수 가능 금액을 한 번에 계산
    private RefundCalculation calculateRefund(Payment payment, Long customerId) {
        PointRefundPreview pointRefundPreview =
                pointTransactionService.previewRefundImpact(customerId, payment.getOrder().getId());

        long recoverableEarnedPoints =
                pointRefundPreview.getEarnedPointsToCancel() - pointRefundPreview.getUnrecoverableEarnedPoints();
        long cancelAmount = payment.getPgAmount() - pointRefundPreview.getUnrecoverableEarnedPoints();

        return new RefundCalculation(
                cancelAmount,
                recoverableEarnedPoints,
                pointRefundPreview.getUnrecoverableEarnedPoints()
        );
    }
}
