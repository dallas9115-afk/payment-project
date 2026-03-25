package com.bootcamp.paymentdemo.domain.subscription.service;

import com.bootcamp.paymentdemo.domain.subscription.entity.Subscription;
import com.bootcamp.paymentdemo.domain.subscription.entity.SubscriptionBilling;
import com.bootcamp.paymentdemo.domain.subscription.entity.SubscriptionStatus;
import com.bootcamp.paymentdemo.domain.subscription.repository.SubscriptionBillingRepository;
import com.bootcamp.paymentdemo.domain.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {


    // 유예 기간
    private static final int PAST_DUE_DAYS = 7;

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionBillingRepository subscriptionBillingRepository;

    // 구독 생성 -> 년간, 월간 -> 결제 성공 -> ACTIVE(구독 활성)
    @Transactional
    public void activateSubscription(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("구독이 없습니다. id는" + subscriptionId));

        if (subscription.getStatus() == SubscriptionStatus.ENDED) {
            throw new IllegalStateException("종료된 구독은 활성화할 수 없습니다.");
        }

        if (subscription.getStatus() == SubscriptionStatus.CANCELED) {
            throw new IllegalStateException("해지된 구독은 다시 활성화할 수 없습니다.");
        }
        if(subscription.getStatus()==SubscriptionStatus.BAN){
            throw new IllegalArgumentException("차단된 사용자입니다. 고객센터에 문의하세요");
        }

        subscription.activate();
    }

    // 구독 생성 -> 년간, 월간 -> 결제 실패 -> PAST_DUE(결제 연체)
    @Transactional
    public void markPastDueAfterCreate(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("구독이 없습니다. id는" + subscriptionId));

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("활성 상태 구독만 연체로 변경할 수 있습니다. 현재상태는" + subscription.getStatus());
        }

        // PAST_DUE(결제 연체) 상태 변화.
        subscription.markPastDue();
    }

    // PAST_DUE(결제 연체) -> 기간 7일 이내 잔금 지불 함 -> 구독 활성
    @Transactional
    public void recoverPastDueSubscription(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("구독이 없습니다. id는" + subscriptionId));

        if (subscription.getStatus() != SubscriptionStatus.PAST_DUE) {
            throw new IllegalStateException("연체 상태 구독만 복구할 수 있습니다. 현재상태는" + subscription.getStatus());
        }

        if (isPastDueGraceExpired(subscription)) {
            throw new IllegalStateException("연체 유예 기간이 지나 복구할 수 없습니다.");
        }

        subscription.activate();
    }

    // PAST_DUE(결제 연체) -> 기간 7일 이내 잔금 지불 안함 -> BAN(사용자 차단)
    @Transactional
    public void endPastDueSubscription(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("구독이 없습니다. id는" + subscriptionId));

        if (subscription.getStatus() != SubscriptionStatus.PAST_DUE) {
            throw new IllegalStateException("연체 상태 구독만 종료할 수 있습니다. 현재상태는" + subscription.getStatus());
        }

        if (!isPastDueGraceExpired(subscription)) {
            throw new IllegalStateException("아직 연체 유예 기간이 남아 있습니다.");
        }

        subscription.ban();
    }

    // 구독 활성 -> 구독 갱신 -> 년간, 월간 두개 중 골라서 기간 연장
    @Transactional
    public void renewSubscription(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("구독이 없습니다. i는" + subscriptionId));

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("활성 구독만 갱신할 수 있습니다. 현재상태는" + subscription.getStatus());
        }

        // 구독 갱신
        subscription.renew();
    }

    // 구독 활성 -> 취소 요청 -> CANCELED(해지됨)
    @Transactional
    public void cancelSubscription(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("구독이 없습니다. id는" + subscriptionId));

        if (subscription.getStatus() == SubscriptionStatus.ENDED) {
            throw new IllegalStateException("이미 종료된 구독입니다.");
        }

        if (subscription.getStatus() == SubscriptionStatus.CANCELED) {
            return;
        }

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE
                && subscription.getStatus() != SubscriptionStatus.PAST_DUE) {
            throw new IllegalStateException("해지할 수 없는 구독 상태입니다. 현재상태는" + subscription.getStatus());
        }

        subscription.cancel();
    }

    // CANCELED(해지됨) -> 구독 기간 종료 -> ENDED(이용 종료)
    @Transactional
    public void endCanceledSubscription(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("구독이 없습니다. id는" + subscriptionId));

        if (subscription.getStatus() != SubscriptionStatus.CANCELED) {
            throw new IllegalStateException("해지된 구독만 종료할 수 있습니다. 현재상태는" + subscription.getStatus());
        }

        if (subscription.getCurrentPeriodEnd().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("아직 남은 구독 기간이 있습니다.");
        }

        subscription.end();
    }

    // 구독 활성 -> 구독 기간 종료 -> 잔여일 없음, 잔금 없음 -> ENDED(이용 종료)
    @Transactional
    public void endSubscriptionWithoutRemainingDays(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("구독이 없습니다. id=" + subscriptionId));

        long remainingDays = calculateRemainingDays(subscription);
        long settlementAmount = calculateSettlementAmount(subscription);

        if (remainingDays > 0) {
            throw new IllegalStateException("아직 남은 기간이 있어 종료할 수 없습니다. 잔여일은 " + remainingDays+" 입니다.");
        }

        if (settlementAmount > 0) {
            throw new IllegalStateException("잔금이 남아 있어 종료할 수 없습니다. 잔금은 " + settlementAmount+" 입니다.");
        }

        // 이용 종료
        subscription.end();
    }

    // 구독 활성 -> 구독 기간 종료 -> 잔여일 있음, 잔금 지불 O -> CANCELED(해지됨)
    @Transactional
    public void cancelSubscriptionAfterSettlementPaid(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("구독이 없습니다. id는" + subscriptionId));

        long remainingDays = calculateRemainingDays(subscription);
        long settlementAmount = calculateSettlementAmount(subscription);

        if (remainingDays <= 0) {
            throw new IllegalStateException("남은 기간이 없는 구독은 잔금 정산 해지가 필요하지 않습니다.");
        }

        if (settlementAmount <= 0) {
            throw new IllegalStateException("지불할 잔금이 없습니다.");
        }

        subscriptionBillingRepository.save(
                SubscriptionBilling.success(
                        subscription,
                        settlementAmount,
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        subscription.getCurrentPeriodEnd()
                )
        );

        subscription.cancel();
    }

    // 구독 활성 -> 구독 기간 종료 -> 잔여일 있음, 잔금 지불 X -> BAN(사용자 차단)
    @Transactional
    public void markPastDueAfterSettlementFailure(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("구독이 없습니다. id는" + subscriptionId));

        long remainingDays = calculateRemainingDays(subscription);
        long settlementAmount = calculateSettlementAmount(subscription);

        if (remainingDays <= 0) {
            throw new IllegalStateException("남은 기간이 없는 구독은 잔금 미납 처리 대상이 아닙니다.");
        }

        if (settlementAmount <= 0) {
            throw new IllegalStateException("지불할 잔금이 없습니다.");
        }

        subscriptionBillingRepository.save(
                SubscriptionBilling.fail(
                        subscription,
                        settlementAmount,
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        subscription.getCurrentPeriodEnd(),
                        "잔여 기간 정산 금액 미납입니다.."
                )
        );

        // 바로 사용자를 벤으로 처리.
        subscription.ban();
    }

    // 유예 기간 판단.
    // 현재 구독 주기의 종료 시각을 가져온다.
    // 유예 기간 7일 추가
    // plusDays(7) 따라서 +7이 된다.
    // 그리고 isBefore은
    //  유예기간 만료 시점 < 지금 이면 true
    // 아직 안 지났으면 false
    private boolean isPastDueGraceExpired(Subscription subscription) {
        return subscription.getCurrentPeriodEnd()
                .plusDays(PAST_DUE_DAYS)
                .isBefore(LocalDateTime.now());
    }

    // subscription.getCurrentPeriodEnd() 종료 시각을 가져온다.
    // .isAfter(LocalDateTime.now()) currentPeriodEnd가 지금보다 이후인지 확인하는 로직
    // 이후가 아니라면 현재와 같거나, 이미 지난 일.
    private long calculateRemainingDays(Subscription subscription) {
        if (!subscription.getCurrentPeriodEnd().isAfter(LocalDateTime.now())) {
            // 구독 종료일이 지났으면, 음수 일수 같은 걸 반환하지 않고 그냥 0으로 처리한다.
            return 0L;
        }

        // 아직 남아 있으면 일수 계산
        // between(현재시간, 종료시간) 남은 일수가 계산
        // 즉, between(3월 10일 12:00, 3월 15일 12:00) == 5가 된다. 그러면 5를 반환한다.
        return ChronoUnit.DAYS.between(LocalDateTime.now(), subscription.getCurrentPeriodEnd());
    }

    // 구독을 중간에 해지했을 때 정산금을 계산하는 로직이다.
    private long calculateSettlementAmount(Subscription subscription) {
        // 남은 일수 계산
        long remainingDays = calculateRemainingDays(subscription);
        if (remainingDays <= 0) {
            return 0L;
        }

        // 0이면 에러가 발생해서 1L로 최소값 보장.
        long totalDays = Math.max(1L, ChronoUnit.DAYS.between(
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd()
        ));

        return subscription.getPlan().getPrice() * remainingDays / totalDays;
    }

}
