package com.bootcamp.paymentdemo.domain.point.service;


import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.customer.repository.CustomerRepository;
import com.bootcamp.paymentdemo.domain.point.repository.PointDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointValidationService {
    //정합성 검증용 로직입니다! 포인트 총 발행액과 스냅샷의 합으로 검증합니다!
    private final CustomerRepository customerRepository;
    private final PointDetailRepository detailRepository;

    @Transactional(readOnly = true)
    public void checkIntegrity(Long customerId) {

        // 1. 고객 스냅샷 잔액 조회
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(()-> new IllegalStateException("사용자 없음"));
        Long snapshotBalance = customer.getCurrentPoint();

        // 2. 상세 내역(Detail)의 remainAmount 합계 조회(JPQL 활용)
        Long detailsSum = detailRepository.sumRemainAmountByCustomerId(customerId);
        detailsSum = (detailsSum == null) ? 0L : detailsSum;

        // 3. 비교 및 로그 기록
        if (!snapshotBalance.equals((detailsSum))){
            log.error("[정합성 오류 발생] 고객ID: {}, 스냅샷: {}, 상세합계: {}, 차이: {}",
                    customerId, snapshotBalance, detailsSum, (snapshotBalance - detailsSum));
        } else {
            log.info("[정합성 일치] 고객ID: {} - 잔액 검증 완료", customerId);
        }




    }


}
