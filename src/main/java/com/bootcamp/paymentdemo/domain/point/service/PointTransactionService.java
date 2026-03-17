package com.bootcamp.paymentdemo.domain.point.service;


import com.bootcamp.paymentdemo.domain.point.entity.PointTransactionEntity;
import com.bootcamp.paymentdemo.domain.point.entity.PointType;
import com.bootcamp.paymentdemo.domain.point.repository.PointTransactionRepository;
import com.bootcamp.paymentdemo.global.error.CommonError;
import com.bootcamp.paymentdemo.global.error.CommonException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PointTransactionService {

    private final PointTransactionRepository pointTransactionRepository;

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
    //타임아웃이나 lock 충돌시 발생 메시지


    //잔액 계산 method
    private int calculateNextBalance(int current, PointType type, int points) {
        // 검증(+증감[포인트를 얻거나 환불받았을때], -차감[Hold나 USED 상태일때])
        if (type == PointType.EARNED || type == PointType.ROLLBACK) {
            return current + points;
        }

        return current - points;
    }
}


