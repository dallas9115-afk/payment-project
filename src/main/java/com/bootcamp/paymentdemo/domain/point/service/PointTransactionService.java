package com.bootcamp.paymentdemo.domain.point.service;


import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.customer.entity.UserMembership;
import com.bootcamp.paymentdemo.domain.customer.repository.CustomerRepository;
import com.bootcamp.paymentdemo.domain.customer.repository.UserMembershipRepository;
import com.bootcamp.paymentdemo.domain.customer.service.MembershipService;
import com.bootcamp.paymentdemo.domain.order.entity.Order;
import com.bootcamp.paymentdemo.domain.order.entity.OrderStatus;
import com.bootcamp.paymentdemo.domain.order.repository.OrderRepository;
import com.bootcamp.paymentdemo.domain.point.entity.*;
import com.bootcamp.paymentdemo.domain.point.repository.PointDetailRepository;
import com.bootcamp.paymentdemo.domain.point.repository.PointHistoryRepository;
import com.bootcamp.paymentdemo.global.error.CommonError;
import com.bootcamp.paymentdemo.global.error.CommonException;
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
@Transactional
@Slf4j
public class PointTransactionService {

    private final PointDetailRepository pointDetailRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final CustomerRepository customerRepository;
    private final UserMembershipRepository userMembershipRepository;
    private final OrderRepository orderRepository;
    private final MembershipService membershipService;


    //잔액 계산 method
    private int calculateNextBalance(int current, PointType type, int points) {
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

        // 1. [멱등성 체크] 해당 주문번호로 기 적립된 내역이 있는지 확인.
        // PointDetail에 orderId에 있으니 이를 활용함.
        boolean alreadyEarned = pointDetailRepository.existsByOrderId(String.valueOf(orderId));
        if (alreadyEarned) {
            log.warn("이미 적립이 완료된 주문입니다. (OrderId : {}", orderId);
            return;
        }


        // 2. 유저 멤버십 및 등급 정책 조회
        UserMembership membership = userMembershipRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 사용자 입니다."));

        // 3. [정밀 계산] BigDecimal을 이용한 등급별 적립금 산출
        BigDecimal amount = BigDecimal.valueOf(paidAmount);
        BigDecimal rate = BigDecimal.valueOf(membership.getGradePolicy().getPointRate());
        Long earnedAmount = amount.multiply(rate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        log.info("유저 {}({}) 적립 계산: {}원 * {}% = {}P",
                customerId, membership.getGradePolicy().getGradeName(), paidAmount, rate.multiply(BigDecimal.valueOf(100)), earnedAmount);

        // 4. [스냅샷] customer의 현재 잔액 업데이트 (조회 성능용)
        Customer customer = membership.getCustomer();
        Long beforePoint = customer.getCurrentPoint();
        customer.addPoint(earnedAmount);


        // 5. [상세 내역] PointDetail 저장
        PointDetail detail = PointDetail.builder()
                .customerId(customerId)
                .orderId(String.valueOf(orderId))
                .initialAmount(earnedAmount.intValue())
                .remainAmount(earnedAmount.intValue())
                .expiredAt(LocalDateTime.now().plusYears(1))
                .status(PointStatus.ACCUMULATED)
                .build();

        pointDetailRepository.save(detail);

        // 6. [이력 기록] PointHistory 저장
        saveHistory(customer, detail, PointType.EARNED, earnedAmount, beforePoint, customer.getCurrentPoint(), String.valueOf(orderId), "상품 구매 적립");
    }

    // 포인트 환불
    @Transactional
    public void refundUsedPoints(String orderId) {
        // 1. 멱등성 확인
        if(pointHistoryRepository.existsByOrderIdAndType(orderId, PointType.ROLLBACK)) {
            log.warn("이미 포인트 환원 처리가 완료된 주문입니다. (OrderId:{})", orderId);
            return;
        }

        // 2. 주문 상태 검증
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("주문 정보를 찾을 수 없습니다."));

        if (!order.getStatus().equals(OrderStatus.PAID)) {
            throw new IllegalStateException("환불 가능한 상태의 주문이 아닙니다.");
        }


        // 3. 타입 일치 (PointType.USED 사용)
        List<PointHistory> usageHistories = pointHistoryRepository.findAllByOrderIdAndType(orderId, PointType.USED);
        if (usageHistories.isEmpty()) {
            log.info("해당 주문({})에 대한 포인트 사용 내역이 없습니다.", orderId);
            return;
        }

        // 4. getCustomerId() 대신 getCustomer().getCustomer() 사용
        Long customerId = usageHistories.get(0).getCustomer().getId();

        // 5. 유저 스냅샷 복구
        // findByIdWithLock이 Optional을 반환하는지 repository에서 확인 필수!
        Customer customer = customerRepository.findByIdWithLock(customerId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 유저입니다."));  // TODO: 바꿔야함


        long totalRestoredAmount = 0;

        // 6. 복구 루프
        for (PointHistory history : usageHistories) {
            PointDetail detail = history.getPointDetail();
            if (detail != null) {
                int restoreAmount = Math.abs(history.getAmount().intValue());
                detail.cancelUsage(restoreAmount);
                totalRestoredAmount += restoreAmount;
            }
        }

        customer.addPoint(totalRestoredAmount);

        // 7. ROLLBACK 이력 남기기

        pointHistoryRepository.save(PointHistory.builder()
                .customer(customer)
                .pointDetail(null) // 전체 복구이므로 특정 detail에 종속시키지 않거나 루프 내에서 기록
                .type(PointType.ROLLBACK)
                .amount(totalRestoredAmount)
                .beforePoint(customer.getCurrentPoint() - totalRestoredAmount)
                .afterPoint(customer.getCurrentPoint())
                .orderId(orderId)
                .reason("주문 취소에 따른 포인트 복구: " + orderId)
                .build());

        // 8. 등급 재계산
        membershipService.refreshUserMembership(customerId);
        log.info("유저 {} 환불에 따른 등급 재계산 완료", customerId);
    }


    // 포인트 회수
    @Transactional
    public void cancelEarnedPoints(String orderId) {
        // 1. [멱등성 체크] CANCEL 이력이 이미 있는지 확인
        if (pointHistoryRepository.existsByOrderIdAndType(orderId, PointType.CANCEL)) {
            log.warn("이미 적립 포인트 회수(CANCEL) 처리가 완료된 주문입니다. (OrderId: {})", orderId);
            return;
        }

        // 2. 해당 주문으로 적립된 상세 내역 조회
        List<PointDetail> earnedDetails = pointDetailRepository.findAllByOrderId(orderId);
        if (earnedDetails.isEmpty()) return;

        Long customerId = earnedDetails.get(0).getCustomerId();
        Customer customer = customerRepository.findByIdWithLock(customerId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 유저 입니다."));

        // 3. 회수 대상 총액 계산
        long totalToRecover = earnedDetails.stream()
                .mapToLong(PointDetail::getInitialAmount)
                .sum();

        // 4. [데이터 정합성] 변동 전 스냅샷 기록
        Long beforePoint = customer.getCurrentPoint();

        try {
            // 5.  잔액 검증 및 차감 -> 마이너스 잔액 방지
            customer.deductPoint(totalToRecover);
            Long afterPoint = customer.getCurrentPoint();

            // 6. 상세 내역 상태 변경
            for (PointDetail detail : earnedDetails) {
                detail.cancel(); // 상태를 EXPIRED 또는 CANCELED로 변경
            }

            // 7. 기록
            saveHistory(customer, null, PointType.CANCEL, -totalToRecover, beforePoint, afterPoint, orderId, "주문 취소로 인한 적립 포인트 회수: " + orderId);


            // 8. 등급 재계산
            membershipService.refreshUserMembership(customerId);

        } catch (IllegalStateException e) {
            log.error("[회수 실패] 잔액 부족. 주문: {}, 필요: {}, 현재: {}",
                    orderId, totalToRecover, beforePoint);
            throw new CommonException(CommonError.INSUFFICIENT_BALANCE);
        }
    }

    // 공통 이력 저장 로직 (Dead Code 제거 및 중복 방지)
    private void saveHistory(
            Customer customer,
            PointDetail detail,
            PointType type,
            Long amount,
            Long beforePoint,
            Long afterPoint,
            String orderId,
            String reason
    ) {
        pointHistoryRepository.save(PointHistory.builder()
                .customer(customer)
                .pointDetail(detail)
                .type(type)
                .amount(amount)
                .beforePoint(beforePoint)
                .afterPoint(afterPoint)
                .orderId(orderId)
                .reason(reason)
                .build());
    }

    // 단순 잔액 조회
    @Transactional(readOnly = true)
    public Long getPointBalance(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalStateException("유저가 없습니다.")); //
        return customer.getCurrentPoint(); //
    }


}


