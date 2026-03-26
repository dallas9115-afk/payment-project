package com.bootcamp.paymentdemo.domain.payment.service;

import com.bootcamp.paymentdemo.config.PortOneProperties;
import com.bootcamp.paymentdemo.domain.payment.dto.Response.PortOneCancelResponse;
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

    private final RestTemplate restTemplate = new RestTemplate();
    private final PortOneProperties portOneProperties;

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

    public PortOnePaymentInfoResponse paymentCancel(
            String paymentId,
            String reason,
            String idempotencyKey,
            Long amount
    ) {
        String url = portOneProperties.getApi().getBaseUrl() + "/payments/" + paymentId + "/cancel";
        HttpHeaders headers = buildJsonHeaders(idempotencyKey);
        Map<String, Object> body = buildCancelRequestBody(reason, amount);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        return executeCancelRequest(paymentId, reason, amount, url, entity);
    }

    private HttpHeaders buildJsonHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "PortOne " + portOneProperties.getApi().getSecret());
        headers.set("Idempotency-Key", quoteIdempotencyKey(idempotencyKey));
        headers.set("Content-Type", "application/json");
        return headers;
    }

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
            ResponseEntity<PortOneCancelResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    PortOneCancelResponse.class
            );

            PortOneCancelResponse responseBody = response.getBody();
            if (responseBody == null) {
                throw new IllegalArgumentException("포트원 결제 취소 응답이 비어 있습니다.");
            }

            PortOnePaymentInfoResponse resolvedPaymentInfo = finalizeCancelResponse(paymentId, amount, responseBody);
            log.info("포트원 결제 취소 성공 - paymentId={}, status={}, amount={}",
                    paymentId, resolvedPaymentInfo.getStatus(), resolvedPaymentInfo.resolveTotalAmount());
            return resolvedPaymentInfo;

        } catch (ResourceAccessException e) {
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
     * [개선] 멱등키(idempotencyKey)를 외부에서 주입받도록 파라미터 추가
     */
    public boolean payWithBillingKey(String billingKey, String paymentId, int amount, String idempotencyKey) {
        String url = portOneProperties.getApi().getBaseUrl() + "/payments/" + paymentId + "/billing-key";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "PortOne " + portOneProperties.getApi().getSecret());

        // [개선] 파라미터로 받은 멱등키를 안전하게 헤더에 세팅 (재시도 방어)
        headers.set("Idempotency-Key", quoteIdempotencyKey(idempotencyKey));
        headers.set("Content-Type", "application/json");

        String requestBody = String.format(
                "{\"billingKey\":\"%s\",\"orderName\":\"월간 정기 구독\",\"amount\":{\"total\":%d},\"currency\":\"KRW\"}",
                billingKey, amount
        );

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        try {
            log.info("포트원 빌링키 결제 요청 - paymentId={}, amount={}, idempotencyKey={}", paymentId, amount, idempotencyKey);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("포트원 빌링키 결제 성공 - paymentId={}, response={}", paymentId, response.getBody());
            return true;
        } catch (Exception e) {
            log.error("포트원 빌링키 결제 실패 - paymentId={}, message={}", paymentId, e.getMessage(), e);
            return false;
        }
    }

    public String buildVerifyIdempotencyKey(String paymentId) {
        return "pay:" + paymentId + ":verify:v1";
    }

    public String buildCancelIdempotencyKey(String paymentId) {
        return "pay:" + paymentId + ":cancel:full:v1";
    }

    /**
     * [개선] 멱등키를 안전하게 처리. 이중 따옴표가 중복되지 않도록 방어.
     */
    private String quoteIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            // 값이 안 들어왔다면 기본적으로 랜덤 UUID 생성 (마지막 수단)
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

    private PortOnePaymentInfoResponse finalizeCancelResponse(
            String paymentId,
            Long amount,
            PortOneCancelResponse cancelResponse
    ) {
        String cancellationStatus = cancelResponse.resolveCancellationStatus();
        PortOnePaymentInfoResponse paymentInfo = tryResolveCancelledPayment(paymentId);
        log.info("포트원 결제 취소 응답 파싱 - paymentId={}, cancellationStatus={}, cancellationAmount={}, recheckStatus={}",
                paymentId,
                cancellationStatus,
                cancelResponse.resolveCancellationAmount() != null ? cancelResponse.resolveCancellationAmount() : amount,
                paymentInfo == null ? "<recheck-failed>" : paymentInfo.getStatus());

        if (paymentInfo != null) {
            return paymentInfo;
        }

        return PortOnePaymentInfoResponse.ofCancellation(cancellationStatus, cancelResponse.resolveCancellationAmount());
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

    public void unsubscribeBillingKey(String billingKey) {
        String url = portOneProperties.getApi().getBaseUrl() + "/billing-keys/" + billingKey;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "PortOne " + portOneProperties.getApi().getSecret());
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
            log.error("포트원 빌링키 해지 HTTP 오류 - billingKey={}, status={}, body={}",
                    billingKey, statusCode, e.getResponseBodyAsString());

            if (statusCode != 404) {
                throw new PortOneApiException("빌링키 해지 중 오류가 발생했습니다. (" + statusCode + ")", false);
            }
        } catch (Exception e) {
            log.error("포트원 빌링키 해지 통신 실패 - billingKey={}, message={}", billingKey, e.getMessage());
            throw new PortOneApiException("빌링키 해지 통신 중 알 수 없는 오류 발생", true);
        }
    }
}