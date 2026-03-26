package com.bootcamp.paymentdemo.domain.payment.service;

import com.bootcamp.paymentdemo.config.PortOneProperties;
import com.bootcamp.paymentdemo.domain.payment.dto.Response.PortOnePaymentInfoResponse;
import com.bootcamp.paymentdemo.global.error.PortOneApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.Map;
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
    public PortOnePaymentInfoResponse getPaymentInfo(String paymentId, String idempotencyKey) {
        String url = portOneProperties.getApi().getBaseUrl() + "/payments/" + paymentId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "PortOne " + portOneProperties.getApi().getSecret());
        headers.set("Idempotency-Key", quoteIdempotencyKey(idempotencyKey));

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        log.info("포트원 결제 단건조회 요청 - paymentId={}, url={}, storeIdMask={}, secretMask={}",
                paymentId,
                url,
                mask(portOneProperties.getStore().getId()),
                mask(portOneProperties.getApi().getSecret()));

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

        } catch (ResourceAccessException e) {
            // 네트워크 단절/타임아웃 계열: 재시도 대상
            log.error("포트원 결제 단건조회 네트워크 오류 - paymentId={}, message={}", paymentId, e.getMessage(), e);
            throw new PortOneApiException("포트원 결제 조회 네트워크 오류", true);
        } catch (RestClientResponseException e) {
            int statusCode = e.getStatusCode().value();
            String responseBody = e.getResponseBodyAsString();
            boolean paymentNotFoundYet = statusCode == 404 && responseBody != null
                    && responseBody.contains("PAYMENT_NOT_FOUND");
            log.error("포트원 결제 단건조회 HTTP 오류 - paymentId={}, status={}, body={}",
                    paymentId, statusCode, responseBody, e);
            boolean retryable = statusCode >= 500 || statusCode == 429 || paymentNotFoundYet;
            throw new PortOneApiException("포트원 결제 조회 HTTP 오류(" + statusCode + ")", retryable);
        } catch (PortOneApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("포트원 결제 단건조회 실패 - paymentId={}, message={}", paymentId, e.getMessage(), e);
            throw new PortOneApiException("포트원 결제 조회 중 오류가 발생했습니다.", true);
        }
    }

    /**
     * 포트원 결제 취소(환불) 요청
     *
     * 문서 기준으로 reason은 body에 넣어야 하므로 JSON 바디로 전달합니다.
     */
    public PortOnePaymentInfoResponse paymentCancel(
            String paymentId,
            String reason,
            String idempotencyKey,
            Long amount // 기본값 null
    ) {
        String url = portOneProperties.getApi().getBaseUrl() + "/payments/" + paymentId + "/cancel";
        HttpHeaders headers = buildJsonHeaders(idempotencyKey);
        Map<String, Object> body = buildCancelRequestBody(reason, amount);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        return executeCancelRequest(paymentId, reason, amount, url, entity);
    }

    // 멱등키 헤더
    private HttpHeaders buildJsonHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "PortOne " + portOneProperties.getApi().getSecret());
        headers.set("Idempotency-Key", quoteIdempotencyKey(idempotencyKey));
        headers.set("Content-Type", "application/json");
        return headers;
    }

    // 바디 만들기
    private Map<String, Object> buildCancelRequestBody(String reason, Long amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("reason", reason);
        body.put("storeId", portOneProperties.getStore().getId());
        body.put("skipWebhook", false);

        if (amount != null) {
            body.put("amount", amount);
        }
        return body;
    }

    private PortOnePaymentInfoResponse executeCancelRequest(
            String paymentId,
            String reason,
            Long amount,
            String url,
            HttpEntity<Map<String, Object>> entity
    ) {
        try {
            log.info("포트원 결제 취소 요청 - paymentId={}, amount={}, reason={}, storeIdMask={}, secretMask={}",
                    paymentId,
                    amount,
                    reason,
                    mask(portOneProperties.getStore().getId()),
                    mask(portOneProperties.getApi().getSecret()));
            ResponseEntity<PortOnePaymentInfoResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    PortOnePaymentInfoResponse.class
            );

            PortOnePaymentInfoResponse responseBody = response.getBody();
            if (responseBody == null) {
                throw new IllegalArgumentException("포트원 결제 취소 응답이 비어 있습니다.");
            }

            log.info("포트원 결제 취소 성공 - paymentId={}, status={}, amount={}",
                    paymentId, responseBody.getStatus(), responseBody.resolveTotalAmount());
            return responseBody;

        } catch (ResourceAccessException e) {
            // 네트워크 단절/타임아웃 계열: 재시도 대상
            log.error("포트원 결제 취소 네트워크 오류 - paymentId={}, message={}", paymentId, e.getMessage(), e);
            throw new PortOneApiException("포트원 결제 취소 네트워크 오류", true);
        } catch (RestClientResponseException e) {
            int statusCode = e.getStatusCode().value();
            String responseBody = e.getResponseBodyAsString();
            log.error("포트원 결제 취소 HTTP 오류 - paymentId={}, status={}, body={}",
                    paymentId, statusCode, responseBody, e);

            if (statusCode == 400) {
                PortOnePaymentInfoResponse paymentInfo = tryResolveCancelledPayment(paymentId);
                log.warn("포트원 결제 취소 400 상세 - paymentId={}, amount={}, responseBody={}, recheckStatus={}",
                        paymentId,
                        amount,
                        responseBody,
                        paymentInfo == null ? "<recheck-failed>" : paymentInfo.getStatus());
                if (paymentInfo != null && paymentInfo.isCancelledStatus()) {
                    log.warn("포트원 취소 응답은 400이지만 이미 취소 완료 상태입니다. paymentId={}, status={}",
                            paymentId, paymentInfo.getStatus());
                    return paymentInfo;
                }
            }

            boolean retryable = statusCode >= 500 || statusCode == 429;
            throw new PortOneApiException("포트원 결제 취소 HTTP 오류(" + statusCode + ")", retryable);
        } catch (PortOneApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("포트원 결제 취소 실패 - paymentId={}, message={}", paymentId, e.getMessage(), e);
            throw new PortOneApiException("포트원 결제 취소 중 오류가 발생했습니다.", true);
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

    public String buildVerifyIdempotencyKey(String paymentId) {
        // "같은 verify 작업은 같은 키"를 보장하기 위한 결정적 키
        return "pay:" + paymentId + ":verify:v1";
    }

    public String buildCancelIdempotencyKey(String paymentId) {
        // "같은 cancel 작업은 같은 키"를 보장하기 위한 결정적 키
        return "pay:" + paymentId + ":cancel:full:v1";
    }

    private String quoteIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return "\"" + UUID.randomUUID() + "\"";
        }
        if (idempotencyKey.startsWith("\"") && idempotencyKey.endsWith("\"")) {
            return idempotencyKey;
        }
        return "\"" + idempotencyKey + "\"";
    }

    private PortOnePaymentInfoResponse tryResolveCancelledPayment(String paymentId) {
        try {
            return getPaymentInfo(paymentId, buildVerifyIdempotencyKey(paymentId));
        } catch (Exception e) {
            log.warn("포트원 취소 후 상태 재조회 실패 - paymentId={}, message={}", paymentId, e.getMessage());
            return null;
        }
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        if (value.length() <= 8) {
            return value.charAt(0) + "***" + value.charAt(value.length() - 1);
        }
        return value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }

    /**
     * 포트원 빌링키 해지(삭제) 요청
     * - 구독 해지 시 호출하여 다음 결제가 일어나지 않도록 방지합니다.
     *
     * @param billingKey 삭제할 빌링키
     */
    public void unsubscribeBillingKey(String billingKey) {
        // 포트원 V2 빌링키 삭제 엔드포인트
        String url = portOneProperties.getApi().getBaseUrl() + "/billing-keys/" + billingKey;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "PortOne " + portOneProperties.getApi().getSecret());
        // DELETE 요청도 안전을 위해 멱등키를 생성해서 보냅니다.
        headers.set("Idempotency-Key", quoteIdempotencyKey("unsub-" + billingKey));

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            log.info("포트원 빌링키 해지 요청 시작 - billingKey={}", billingKey);

            restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    entity,
                    Void.class
            );

            log.info("포트원 빌링키 해지 성공 - billingKey={}", billingKey);

        } catch (RestClientResponseException e) {
            int statusCode = e.getStatusCode().value();
            // 이미 삭제된 키인 경우(404)는 성공으로 간주해도 무방하지만, 일단 로그를 남깁니다.
            log.error("포트원 빌링키 해지 HTTP 오류 - billingKey={}, status={}, body={}",
                    billingKey, statusCode, e.getResponseBodyAsString());

            // 404가 아니면 예외를 던져서 서비스 레이어에서 CANCEL_FAILED 처리를 하게 합니다.
            if (statusCode != 404) {
                throw new PortOneApiException("빌링키 해지 중 오류가 발생했습니다. (" + statusCode + ")", false);
            }
        } catch (Exception e) {
            log.error("포트원 빌링키 해지 통신 실패 - billingKey={}, message={}", billingKey, e.getMessage());
            throw new PortOneApiException("빌링키 해지 통신 중 알 수 없는 오류 발생", true);



        }
    }
}
