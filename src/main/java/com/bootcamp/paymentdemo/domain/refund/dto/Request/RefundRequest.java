package com.bootcamp.paymentdemo.domain.refund.dto.Request;

import jakarta.validation.constraints.NotBlank;

public record RefundRequest(
        @NotBlank(message = "취소사유는 필수입니다.")
        String reason
) {
}
