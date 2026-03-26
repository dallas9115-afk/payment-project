package com.bootcamp.paymentdemo.domain.point.service;


import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.customer.repository.CustomerRepository;
import com.bootcamp.paymentdemo.domain.point.entity.PointDetail;
import com.bootcamp.paymentdemo.domain.point.entity.PointHistory;
import com.bootcamp.paymentdemo.domain.point.entity.PointType;
import com.bootcamp.paymentdemo.domain.point.repository.PointDetailRepository;
import com.bootcamp.paymentdemo.domain.point.repository.PointHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class PointExpirationBatch {

    private final PointDetailRepository pointDetailRepository;
    private final CustomerRepository customerRepository;
    private final PointHistoryRepository pointHistoryRepository;

    @Scheduled(cron = "0 0 2 * * *")
    public void expirePointJob() {
        log.info("===포인트 소멸 배치 작업 시작===");
        LocalDateTime now = LocalDateTime.now();
        int pageSize = 500;
        long totalExpiredCount = 0;
        PageRequest pageRequest = PageRequest.of(0, pageSize);

        while (true) {
            // remainAmount >0이고 만료일이 지난 데이터 500건 조회
            // 처리 후 remainAmount가 0이 되므로 항상 0 페이지를 조회
            Slice<PointDetail> expiredSlice = pointDetailRepository.findAllByExpiredAtBeforeAndRemainAmountGreaterThan(now, 0, pageRequest);

            if (expiredSlice.isEmpty()) break;

            for (PointDetail detail : expiredSlice) {
                try {
                    //개별 처리
                    processExpiration(detail);
                    totalExpiredCount++;
                } catch (Exception e) {
                    log.error("포인트 소멸 처리 중 오류 발생 (Detail ID: {}) : {}", detail.getId(), e.getMessage());
                }
            }

                if (!expiredSlice.hasNext()) break;
            }

            log.info("===포인트 소멸 배치 작업 종료(총 {}건 소멸 완료===", totalExpiredCount);
        }

        @Transactional
        public void processExpiration(PointDetail detail) {
            Customer customer = customerRepository.findByIdWithLock(detail.getCustomerId())
                    .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));

            int expireAmount = detail.getRemainAmount();

            //1. 유저 스냅샷 차감
            customer.deductPoint((long) expireAmount);

            //2. 상세 내역 상태 변경(엔티티 메서드 호출)
            detail.expire();

            //3. 이력 남기기
            pointHistoryRepository.save(PointHistory.builder()
                    .customer(customer)
                    .pointDetail(detail)
                    .type(PointType.EXPIRED)
                    .amount((long) -expireAmount)
                    .beforePoint(customer.getCurrentPoint() + expireAmount)
                    .afterPoint(customer.getCurrentPoint())
                    .reason("유효기간 만료에 따른 자동 소멸")
                    .build());
        }


}



