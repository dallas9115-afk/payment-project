package com.bootcamp.paymentdemo.domain.payment.dto.Response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PortOneCancelResponse {

    private Cancellation cancellation;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Cancellation {
        private String status;
        private String id;
        private Long totalAmount;
        private String reason;
    }

    public String resolveCancellationStatus() {
        return cancellation == null ? null : cancellation.getStatus();
    }

    public Long resolveCancellationAmount() {
        return cancellation == null ? null : cancellation.getTotalAmount();
    }
}
