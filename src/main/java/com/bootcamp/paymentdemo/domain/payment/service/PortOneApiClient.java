package com.bootcamp.paymentdemo.domain.payment.service;

import com.bootcamp.paymentdemo.config.PortOneProperties;
import com.bootcamp.paymentdemo.domain.payment.dto.Response.PortOnePaymentInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneApiClient {

    /**
     * 외부 HTTP 통신 도구
     * - 실무에서는 WebClient를 쓰는 경우도 많지만, 현재 프로젝트는 RestTemplate으로 충분합니다.
     */
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * application.yml -> portone.* 설정을 바인딩한 값
     * - API base-url, secret, store-id 등을 여기서 가져옵니다.
     */
    private final PortOneProperties portOneProperties;

    /**
     * PortOne 결제 단건 조회
     *
     * @param paymentId 우리 시스템/PortOne에서 공통으로 사용하는 결제 식별자
     * @return PortOnePaymentInfoResponse (상태, 금액, 상점ID 등 검증용 정보)
     */
    public PortOnePaymentInfoResponse getPaymentInfo(String paymentId) {
        String url = portOneProperties.getApi().getBaseUrl() + "/payments/" + paymentId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "PortOne " + portOneProperties.getApi().getSecret());
        // 외부 API 호출에도 멱등 키를 넣어두면 재시도 상황에서 안전합니다.
        headers.set("Idempotency-Key", "\"" + UUID.randomUUID() + "\"");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<PortOnePaymentInfoResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    PortOnePaymentInfoResponse.class
            );

            PortOnePaymentInfoResponse body = response.getBody();
            if (body == null) {
                throw new IllegalArgumentException("포트원 결제 조회 응답이 비어 있습니다.");
            }

            log.info("포트원 결제 단건조회 성공 - paymentId={}, status={}, amount={}",
                    paymentId, body.getStatus(), body.resolveTotalAmount());
            return body;

        } catch (Exception e) {
            log.error("포트원 결제 단건조회 실패 - paymentId={}, message={}", paymentId, e.getMessage(), e);
            throw new IllegalArgumentException("포트원 결제 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 포트원 빌링키 결제 요청 (정기결제)
     *
     * @param billingKey PortOne에서 발급받아 저장한 빌링키
     * @param paymentId  이번 결제 고유 식별자
     * @param amount     결제 금액
     * @return true: 결제 성공, false: 결제 실패
     */
    public boolean payWithBillingKey(String billingKey, String paymentId, int amount) {
        String url = portOneProperties.getApi().getBaseUrl() + "/payments/" + paymentId + "/billing-key";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "PortOne " + portOneProperties.getApi().getSecret());
        headers.set("Idempotency-Key", "\"" + UUID.randomUUID() + "\"");
        headers.set("Content-Type", "application/json");

        // 최소 요청 바디만 직접 구성
        String requestBody = String.format(
                "{\"billingKey\":\"%s\",\"orderName\":\"월간 정기 구독\",\"amount\":{\"total\":%d}}",
                billingKey, amount
        );

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        try {
            log.info("포트원 빌링키 결제 요청 - paymentId={}, amount={}", paymentId, amount);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("포트원 빌링키 결제 성공 - paymentId={}, response={}", paymentId, response.getBody());
            return true;
        } catch (Exception e) {
            log.error("포트원 빌링키 결제 실패 - paymentId={}, message={}", paymentId, e.getMessage(), e);
            return false;
        }
    }
}
