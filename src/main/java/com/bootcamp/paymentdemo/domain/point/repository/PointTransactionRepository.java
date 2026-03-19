package com.bootcamp.paymentdemo.domain.point.repository;

import com.bootcamp.paymentdemo.domain.point.entity.PointTransactionEntity;
import com.bootcamp.paymentdemo.domain.point.entity.PointType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PointTransactionRepository extends JpaRepository<PointTransactionEntity, Long> {

    // 1. 최신순 조회: 특정 사용자의 내역을 생성일 역순으로 가져오기
    // 필드명(userId)과 정렬 조건(OrderByCreatedAtDesc)을 조합합니다.
    // 비관적 lock을 사용
    // 잔액 상황을 조회하기 위한 일시적 코드 / 언제든 수정 가능 :D
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name="jacx.persistence.lock.timeout",value = "3000")}) //nms라 3초임
    List<PointTransactionEntity> findAllByCustomerIdOrderByCreatedAtDesc(Long userId);
    Optional<PointTransactionEntity> findFirstByCustomerIdOrderByCreatedAtDesc(Long userId);

    // 2. 유형별 필터링: 특정 사용자의 내역 중 특정 타입(예: EARNED)만 조회
    // userId와 type 두 가지 조건을 함께 사용합니다.
    // 잔액 상황을 조회하기 위한 일시적 코드 / 언제든 수정 가능 :D
    List<PointTransactionEntity> findAllByCustomerIdAndType(Long userId, PointType type);

    // 3. 기간 조회: 특정 날짜 사이에 발생한 내역 확인
    // Between 키워드를 사용하여 시작일과 종료일 사이의 데이터를 찾습니다.
    List<PointTransactionEntity> findAllByCustomerIdAndCreatedAtBetween(
            Long userId,
            LocalDateTime start,
            LocalDateTime end
    );



}
