package com.bootcamp.paymentdemo.domain.point.service;


import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.customer.entity.UserMembership;
import com.bootcamp.paymentdemo.domain.customer.repository.CustomerRepository;
import com.bootcamp.paymentdemo.domain.customer.repository.UserMembershipRepository;
import com.bootcamp.paymentdemo.domain.point.entity.*;
import com.bootcamp.paymentdemo.domain.point.repository.PointDetailRepository;
import com.bootcamp.paymentdemo.domain.point.repository.PointHistoryRepository;
import com.bootcamp.paymentdemo.domain.point.repository.PointTransactionRepository;
import com.bootcamp.paymentdemo.global.error.CommonError;
import com.bootcamp.paymentdemo.global.error.CommonException;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Builder
@Transactional
@Slf4j
public class PointTransactionService {

    private final PointTransactionRepository pointTransactionRepository;
    private final PointDetailRepository pointDetailRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final CustomerRepository customerRepository;
    private final UserMembershipRepository userMembershipRepository;


    //잔액 계산 method
    private int calculateNextBalance(int current, PointType type, int points) {
        // 검증(+증감[포인트를 얻거나 환불받았을때], -차감[Hold나 USED 상태일때])
        if (type == PointType.EARNED || type == PointType.ROLLBACK) {
            return current + points;
        }

        return current - points;
    }

    // 포인트 사용
    @Transactional // 데이터 변경이 일어나므로 쓰기 모드, snapshot 메서드
    public void usePoints(Long customerId, Long totalAmountToUse, String orderId) {

        // 1. [비관적 락] 유저 잔액(스냅샷)을 수정하기 위해 DB를 잠금
        // 에러 작성 대기중
        Customer customer = customerRepository.findByIdWithLock(customerId)
                .orElseThrow(() -> new CommonException(CommonError.INSUFFICIENT_BALANCE));

        // 2. [1차 검증] 전체 잔액이 부족하면 상세 내역을 뒤질 필요도 없이 즉시 리턴
        if (customer.getCurrentPoint() < totalAmountToUse) {
            throw new CommonException(CommonError.INSUFFICIENT_BALANCE);
        }

        // 3. [상세 내역 조회] 만료일이 가장 빠른 순서대로, 잔액이 남은 것들만 가져옴
        List<PointDetail> availableDetails = pointDetailRepository
                .findAllByCustomerIdAndRemainAmountGreaterThanOrderByExpiredAtAsc(customerId, 0);

        long remainToDeduct = totalAmountToUse;

        // 4. [순회 차감] 쪼개진 포인트 뭉치들을 하나씩 차감해나감.
        for (PointDetail detail : availableDetails) {
            if (remainToDeduct <= 0) break;

            // 이번 뭉치에서 깎을 수 있는 최대 금액 계산
            int detailRemain = detail.getRemainAmount();
            long deductAmount = Math.min(detailRemain, remainToDeduct);

            // [핵심] 유저 스냅샷 차감 + 이력(History) 객체 생성
            // Customer 내부에서 History 객체를 생성해서 반환함.
            PointHistory history = customer.deductPointWithDetail(
                    detail,
                    deductAmount,
                    orderId,
                    PointType.USED,
                    "과자 결제 사용"
            );

            // 5. 상세 내역(Detail)의 실제 잔액도 깎아줍니다. <- 공간 복잡도 올라감
            detail.use((int) deductAmount);

            // 6. 결과 저장
            pointHistoryRepository.save(history);

            // 남은 차감액 업데이트
            remainToDeduct -= deductAmount;
        }

        // 반복문이 끝났는데도 remainToDeduct > 0 이라면 데이터 정합성에 문제가 있는거임.
        if (remainToDeduct > 0) {
            throw new IllegalStateException("포인트 계산 정합성 오류: 차감할 금액이 남았습니다.");
        }
    }


    //결제 후 포인트 적립
    @Transactional
    public void earnPointAfterPayment(Long customerId, Long orderId, Long paidAmount) {
        // 1. 유저 멤버십 및 등급 정책 조회
        UserMembership membership = userMembershipRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 사용자 입니다."));

        // 2. [정밀 계산] BigDecimal을 이용한 등급별 적립금 산출
        BigDecimal amount = BigDecimal.valueOf(paidAmount);
        BigDecimal rate = BigDecimal.valueOf(membership.getGradePolicy().getPointRate());
        Long earnedAmount = amount.multiply(rate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        log.info("유저 {}({}) 적립 계산: {}원 * {}% = {}P",
                customerId, membership.getGradePolicy().getGradeName(), paidAmount, rate.multiply(BigDecimal.valueOf(100)), earnedAmount);

        // 3. [스냅샷] customer의 현재 잔액 업데이트 (조회 성능용)
        Customer customer = membership.getCustomer();
        customer.addPoint(earnedAmount);


        // 4. [상세 내역] PointDetail 저장
        PointDetail detail = PointDetail.builder()
                .customerId(customerId)
                .orderId(String.valueOf(orderId))
                .initialAmount(earnedAmount.intValue()) // [수정] amount -> initialAmount
                .remainAmount(earnedAmount.intValue())
                .expiredAt(LocalDateTime.now().plusYears(1))
                .status(PointStatus.ACCUMULATED)         // [추가] 엔티티에 nullable=false이므로 상태도 넣어주세요!
                .build();

        pointDetailRepository.save(detail);

        // 5. [이력 기록] PointHistory 저장 (PointTransaction 대신 History로 통합 관리 권장)
        PointHistory history = PointHistory.builder()
                .customer(customer)
                .pointDetail(detail)
                .type(PointType.EARNED)
                .amount(earnedAmount)
                .beforePoint(customer.getCurrentPoint() - earnedAmount)
                .afterPoint(customer.getCurrentPoint())
                .orderId(String.valueOf(orderId))
                .reason("상품 구매 적립 (" + membership.getGradePolicy().getGradeName() + ")")
                .build();
        pointHistoryRepository.save(history);
    }

    // 포인트 환불
    @Transactional
    public void refundUsedPoints(String orderId) {
        // 1. 오타 수정 및 타입 일치 (PointType.USED 사용)
        List<PointHistory> usageHistories = pointHistoryRepository.findAllByOrderIdAndType(orderId, PointType.USED);

        if (usageHistories.isEmpty()) {
            log.info("해당 주문({})에 대한 포인트 사용 내역이 없습니다.", orderId);
            return;
        }

        // 2. getCustomerId() 대신 getCustomer().getCustomer() 사용
        Long customerId = usageHistories.get(0).getCustomer().getId();

        long totalRestoredAmount = 0;

        // 3. 복구 루프
        for (PointHistory history : usageHistories) {
            PointDetail detail = history.getPointDetail();
            if (detail != null) {
                int restoreAmount = Math.abs(history.getAmount().intValue());
                detail.cancelUsage(restoreAmount);
                totalRestoredAmount += restoreAmount;
            }
        }

        // 4. 유저 스냅샷 복구
        // findByIdWithLock이 Optional을 반환하는지 repository에서 확인 필수!
        Customer customer = (Customer) customerRepository.findByIdWithLock(customerId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 유저입니다."));  // TODO: 바꿔야함

        customer.addPoint(totalRestoredAmount);

        // 5. ROLLBACK 이력 남기기
        PointTransactionEntity refundTx = PointTransactionEntity.builder()
                .customerId(customerId)
                .orderId(Long.parseLong(orderId))
                .type(PointType.ROLLBACK)
                .points((int) totalRestoredAmount)
                .balanceAfter(customer.getCurrentPoint().intValue())
                .description("주문 취소에 따른 포인트 복구: " + orderId)
                .build();

        pointTransactionRepository.save(refundTx);
    }


    // 포인트 회수 <- 포인트 회수 정책 고민 필요
    @Transactional
    // 기본 베이스
    public void cancelEarnedPoints2(String orderId) {

        // 1. 해당 주문으로 적립되었던 상세 내역(PointDetail) 들을 찾음
        List<PointDetail> earnedDetails = pointDetailRepository.findAllByOrderId(orderId);

        if(earnedDetails.isEmpty()) {
            log.info("해당 주문({})으로 적립된 포인트 내역이 없습니다.", orderId);
            return;
        }

        long totalCancelAmount = 0;
        Long customerId = earnedDetails.get(0).getCustomerId();

        // 2. 적립되었던 포인트 뭉치들을 '취소' 상태로 변경하고 회수할 총액 계산
        for (PointDetail detail : earnedDetails) {
            // 이미 사용된 포인트라면 회수 시 잔액이 부족해질 수 있음을 인지해야 함
            totalCancelAmount += detail.getInitialAmount();

            // PointStatus.CANCELED 상태로 변경 ( Enum 활용)
            // 엔티티에 cancel() 같은 메서드를 추가해서 상태를 변경하면 좋습니다.
            detail.use(detail.getRemainAmount()); // 남은 잔액을 0으로 만듦
            // detail.setStatus(PointStatus.CANCELED); // 상태 변경 메서드가 있다면 호출
            // 이 부분은 수정중입니다! 취소 부분은 생각보다 고려할게 많아서링...
        }

        // 3. 유저 스냅샷 잔액에서 회수 (addPoint에 음수를 넣거나 deductPoint 활용)
        Customer customer = (Customer) customerRepository.findByIdWithLock(customerId)
                .orElseThrow(() -> new IllegalStateException("회수 대상 유저가 없습니다."));

        // 유저 잔액이 회수액보다 적더라도 일단 차감 (마이너스 잔액 허용 여부는 정책에 따라 결정)
        customer.deductPoint(totalCancelAmount);

        // 4. 이력 남기기 (PointType.SPENT 또는 새로운 CANCEL 타입 활용)
        PointTransactionEntity cancelTx = PointTransactionEntity.builder()
                .customerId(customerId)
                .orderId(Long.parseLong(orderId))
                .type(PointType.USED) // 회수는 포인트가 나가는 것이므로 USED 또는 적절한 타입 사용
                .points((int) -totalCancelAmount)
                .balanceAfter(customer.getCurrentPoint().intValue())
                .description("주문 취소에 따른 적립 포인트 회수: " + orderId)
                .build();

        pointTransactionRepository.save(cancelTx);

        log.info("주문 {} 적립 회수 완료: 총 {}P 차감됨", orderId, totalCancelAmount);
    }

//    @Transactional
//    // 1번방법 : 차감해서 환불해주기
//    public void processNetRefund(String orderId, Long paidCashAmount) {
//        // 1. 해당 주문에서 사용(USED)된 포인트 이력을 모두 찾습니다.
//        List<PointHistory> usageHistories = pointHistoryRepository.findAllByOrderIdAndType(orderId, PointType.USED);
//
//        long usedPointAmount = 0;
//        if (!usageHistories.isEmpty()) {
//            for (PointHistory history : usageHistories) {
//                usedPointAmount += Math.abs(history.getAmount());
//            }
//        }
//
//        // 2. [핵심] 실제 환불할 현금 계산
//        // 이미 포인트를 써버렸다면, 그만큼 현금에서 까고 돌려줍니다.
//        long finalRefundCash = paidCashAmount - usedPointAmount;
//
//        if (finalRefundCash < 0) {
//            // 만약 포인트 사용액이 결제액보다 크다면? (특수 케이스 대응)
//            finalRefundCash = 0;
//            log.warn("주문 {} : 포인트 사용액이 결제액을 초과하여 현금 환불금이 0원입니다.", orderId);
//        }
//
//        // 3. 적립되었던 포인트 회수 (PointDetail 무효화)
//        cancelEarnedPoints(orderId);
//
//        // 4. 외부 결제 시스템(PortOne 등)에 'finalRefundCash' 만큼만 환불 요청
//        // paymentService.requestExternalRefund(orderId, finalRefundCash);
//
//        log.info("주문 {} 복합 결제 환불 완료: 원결제 {}원 - 포인트사용 {}P = 최종환불 {}원",
//                orderId, paidCashAmount, usedPointAmount, finalRefundCash);
//
//        // 4. [추가] 등급 강등(재계산) 실행
//        // 주문 엔티티에서 customerId를 가져와서 멤버십 서비스를 호출합니다.
//        Long customerId = getCustomerIdFromOrder(orderId);
//        membershipService.refreshUserMembership(customerId);
//
//        log.info("주문 {} 환불 및 등급 재계산 완료", orderId);
//    }
//
//    @Transactional
//    // 2번방법 : 마이너스로 만들어버리는 방식
//    public void cancelEarnedPoints(String orderId) {
//        // 1. 해당 주문으로 적립된 내역 찾기
//        List<PointDetail> earnedDetails = pointDetailRepository.findAllByOrderId(orderId);
//
//        long totalToRecover = 0;
//        for (PointDetail detail : earnedDetails) {
//            // [핵심] 적립되었던 포인트 조각을 '무효화' 시킴
//            // 이미 썼든 안 썼든, 이 적립 건의 가용 잔액을 0으로 만들고 상태를 취소로 변경
//            totalToRecover += detail.getInitialAmount(); // 최초 적립액만큼 회수 대상 선정
//            detail.expire(); // 상태를 EXPIRED나 CANCELED로 변경하는 메서드 (엔티티에 추가 필요)
//        }
//
//        // 2. 유저의 전체 잔액에서 차감 (마이너스 허용)
//        Customer customer = (Customer) customerRepository.findByIdWithLock(earnedDetails.get(0).getCustomer().getId())
//                .orElseThrow(() -> new IllegalStateException("유저를 찾을 수 없습니다."));
//
//        // [V] 실무 포인트: 마이너스가 되더라도 과감하게 깎음
//        customer.addPoint(-totalToRecover);
//
//        // 4. [추가] 등급 강등(재계산) 실행
//        // 주문 엔티티에서 userId를 가져와서 멤버십 서비스를 호출합니다.
//        Long CustomerId = getCustomerIdFromOrder(orderId);
//        membershipService.refreshUserMembership(customerId);
//
//        log.info("주문 {} 환불 및 등급 재계산 완료", orderId);
//
//        // 3. 이력 생성 (어떤 이유로 뺏어갔는지 명확히 기록)
//        PointHistory cancelHistory = PointHistory.builder()
//                .customer(customer)
//                .orderId(orderId)
//                .type(PointType.USED) // 또는 CANCEL_EARNED 등 별도 타입
//                .amount(-totalToRecover)
//                .reason("주문 취소에 따른 적립 포인트 회수")
//                .build();
//        pointHistoryRepository.save(cancelHistory);
//
//
//    }







}


