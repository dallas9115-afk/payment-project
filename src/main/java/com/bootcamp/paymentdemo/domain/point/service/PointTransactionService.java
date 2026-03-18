package com.bootcamp.paymentdemo.domain.point.service;


import com.bootcamp.paymentdemo.domain.point.entity.*;
import com.bootcamp.paymentdemo.domain.point.repository.PointDetailRepository;
import com.bootcamp.paymentdemo.domain.point.repository.PointHistoryRepository;
import com.bootcamp.paymentdemo.domain.point.repository.PointTransactionRepository;
import com.bootcamp.paymentdemo.domain.user.entity.User;
import com.bootcamp.paymentdemo.domain.user.repository.UserRepository2;
import com.bootcamp.paymentdemo.global.error.CommonError;
import com.bootcamp.paymentdemo.global.error.CommonException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PointTransactionService {

    private final PointTransactionRepository pointTransactionRepository;
    private final PointDetailRepository pointDetailRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final UserRepository2 userRepository2;

    // 메서드명 고민 필요 / 일단 팀 ERD 기반으로 설정
    public void recordPointHistory(Long userId, Long orderId, Long paymentId, PointType type, Integer points) {

        // 1. 기존 잔액 확인(lastTransaction)
        PointTransactionEntity lastTx = pointTransactionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .orElse(null);

        //if else문이 너무 길어져서 삼항 연산으로 조치합니다 :D
        int currentBalance = (lastTx != null) ? lastTx.getBalanceAfter() : 0;

        // 2. 새로운 잔액 계산(Hold 방식)
        int nextBalance = calculateNextBalance(currentBalance, type, points);

        // 3. 잔액 부족 검증
        if (nextBalance < 0) {
            throw new CommonException(CommonError.INSUFFICIENT_BALANCE);
        }

        // 4. 포인트 사용 이력 저장(newTransaction)
        PointTransactionEntity newTx = PointTransactionEntity.builder()
                .userId(userId)
                .orderId(orderId)
                .paymentId(paymentId)
                .type(type)
                .points(points)
                .balanceAfter(nextBalance)
                .description(type.name())
                .build();

        pointTransactionRepository.save(newTx);
    }


    //잔액 계산 method
    private int calculateNextBalance(int current, PointType type, int points) {
        // 검증(+증감[포인트를 얻거나 환불받았을때], -차감[Hold나 USED 상태일때])
        if (type == PointType.EARNED || type == PointType.ROLLBACK) {
            return current + points;
        }

        return current - points;
    }

    @Transactional // 데이터 변경이 일어나므로 쓰기 모드
    public void usePoints(Long userId, Long totalAmountToUse, String orderId) {

        // 1. [비관적 락] 유저 잔액(스냅샷)을 수정하기 위해 DB를 잠금
        // 에러 작성 대기중
        User user = (User) userRepository2.findByIdWithLock(userId)
                .orElseThrow(() -> new CommonException(CommonError.INSUFFICIENT_BALANCE));

        // 2. [1차 검증] 전체 잔액이 부족하면 상세 내역을 뒤질 필요도 없이 즉시 리턴
        if (user.getCurrentPoint() < totalAmountToUse) {
            throw new CommonException(CommonError.INSUFFICIENT_BALANCE);
        }

        // 3. [상세 내역 조회] 만료일이 가장 빠른 순서대로, 잔액이 남은 것들만 가져옴
        List<PointDetail> availableDetails = pointDetailRepository
                .findAllByUserIdAndRemainAmountGreaterThanOrderByExpiredAtAsc(userId, 0);

        long remainToDeduct = totalAmountToUse;

        // 4. [순회 차감] 쪼개진 포인트 뭉치들을 하나씩 차감해나감.
        for (PointDetail detail : availableDetails) {
            if (remainToDeduct <= 0) break;

            // 이번 뭉치에서 깎을 수 있는 최대 금액 계산
            int detailRemain = detail.getRemainAmount();
            long deductAmount = Math.min(detailRemain, remainToDeduct);

            // [핵심] 유저 스냅샷 차감 + 이력(History) 객체 생성
            // 우리가 만든 그 '통합 메서드'를 여기서 호출합니다!
            PointHistory history = user.deductPointWithDetail(
                    detail,
                    deductAmount,
                    orderId,
                    PointStatus.COMPLETED,
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



}


