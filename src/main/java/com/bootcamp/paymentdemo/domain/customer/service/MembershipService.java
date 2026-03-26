package com.bootcamp.paymentdemo.domain.customer.service;


import com.bootcamp.paymentdemo.domain.customer.dto.response.MembershipRankPolicyResponse;
import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.customer.entity.MembershipRankPolicy;
import com.bootcamp.paymentdemo.domain.order.repository.OrderRepository;
import com.bootcamp.paymentdemo.domain.customer.entity.UserMembership;
import com.bootcamp.paymentdemo.domain.customer.repository.MembershipGradePolicyRepository;
import com.bootcamp.paymentdemo.domain.customer.repository.UserMembershipRepository;
import com.bootcamp.paymentdemo.domain.point.dto.Response.CustomerPointMembershipResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MembershipService {

    private final OrderRepository orderRepository;
    private final UserMembershipRepository userMembershipRepository;
    private final MembershipGradePolicyRepository policyRepository;

    @Transactional
    public void createDefaultMembership(Customer customer) {
        //1. 기본 등급(NORMAL) 정책을 찾아옴.
        MembershipRankPolicy defaultPolicy = policyRepository.findByRankCode("NORMAL")
                .orElseThrow(() -> new IllegalStateException("없는 등급니다."));

        // 반드시 common error 수정해서 넣어야함,

        //2. 유저와 연결된 멤버십 레코드를 생성함.
        UserMembership membership = UserMembership.builder()
                .customer(customer)
                .rankPolicy(defaultPolicy)
                .totalPaidAmount(0L)
                .currentPointRate(defaultPolicy.getPointRate())
                .build();

        userMembershipRepository.save(membership);


    }

    @Transactional
    public void ensureDefaultMembership(Customer customer) {
        if (userMembershipRepository.findByCustomerId(customer.getId()).isPresent()) {
            return;
        }

        createDefaultMembership(customer);
        log.info("기본 멤버십 자동 보정 완료 - customerId={}", customer.getId());
    }

    // 결제후 등급이 바뀌는 메서드
    @Transactional
    public void updateMembershipAfterPayment(Long customerId, Long paidAmount) {

        // 1. 유저의 멤버십 정보 조회
        UserMembership membership = userMembershipRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new IllegalStateException("유저/멤버십 정보를 찾을 수 없습니다."));
        try {
            // 2. 누적 금액 갱신
            Long newTotalAmount = membership.getTotalPaidAmount() + paidAmount;

            // 3. 바뀐 금액에 따른 정책 조회 및 등급 변경 -> minPaidAmount 필드명을 사용해서 가장 적합한 정책 조회
            MembershipRankPolicy newPolicy = policyRepository
                    .findTopByMinPaidAmountLessThanEqualOrderByMinPaidAmountDesc(newTotalAmount)
                    .orElse(membership.getRankPolicy()); // 없으면 현재 유지

            // 4. 멤버십 업데이트 DirtyChecking
            membership.updateMembership(newPolicy, newTotalAmount);

        } catch (Exception e) {
            throw new IllegalStateException("멤버십 갱신 실패" + e.getMessage());
        }


    }

    // 등급 재설정 로직임
    @Transactional
    public void refreshUserMembership(Long customerId) {
        // 1. 해당 유저의 '결제 완료'된 주문들의 총 합계를 구합니다. (환불된 것은 제외)
        // [쿼리 핵심] SELECT SUM(total_price) FROM orders WHERE custemr_id = :customerId AND status = 'PAID'
        Long totalPaidAmount = orderRepository.sumPaidAmountByCustomerId(customerId);
        if (totalPaidAmount == null) totalPaidAmount = 0L;

        // 2. 현재 유저의 멤버십 정보 조회
        UserMembership membership = userMembershipRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new IllegalStateException("멤버십 정보를 찾을 수 없습니다."));

        // 3. 누적 금액에 맞는 적절한 등급 정책 조회
        // [쿼리 핵심] 기준 금액이 누적액 이하인 등급 중 가장 높은 것 하나 선택
        MembershipRankPolicy newPolicy =  policyRepository.findTopByMinPaidAmountLessThanEqualOrderByMinPaidAmountDesc(totalPaidAmount)
                .orElseThrow(() -> new IllegalStateException("적절한 등급 정책이 없습니다."));

        // 4. 등급이 변동되었다면 업데이트
        if (!membership.getRankPolicy().equals(newPolicy)) {
            log.info("유저 {} 등급 변동: {} -> {}",
                    customerId, membership.getRankPolicy().getRankName(), newPolicy.getRankName());
            membership.updateRank(newPolicy);
        }
    }

    // 현재 내 잔액과 등급 조회
    @Transactional(readOnly = true)
    public CustomerPointMembershipResponse.MembershipDto getMembershipSummary(Long customerId) {
        UserMembership membership = userMembershipRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new IllegalStateException("멤버십 정보를 찾을 수 없습니다.")); //

        return CustomerPointMembershipResponse.MembershipDto.builder()
                .grade(membership.getRankPolicy().getRankName()) //
                .benefitRate(membership.getRankPolicy().getPointRate()) //
                .accumulatedAmount(membership.getTotalPaidAmount()) //
                .build();
    }

    //멤버십 정책 조회
    @Transactional(readOnly = true)
    public List<MembershipRankPolicyResponse> getAllMembershipPolicies() {
        // 모든 정책을 조회하여 DTO 리스트로 변환
        return policyRepository.findAll().stream()
                .filter(MembershipRankPolicy::getIsActive) // 활성화된 정책만 필터링
                .map(MembershipRankPolicyResponse::from)
                .toList();
    }



}
