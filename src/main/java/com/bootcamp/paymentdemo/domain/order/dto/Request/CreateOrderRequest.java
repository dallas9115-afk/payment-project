package com.bootcamp.paymentdemo.domain.order.dto.Request;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;


@Getter
@NoArgsConstructor
public class CreateOrderRequest {

    // 프론트엔드 요구에따라 items로 감싸서 보내야함. 따라서 @NoArgsConstructor 사용.
    private List<OrderItem> items;

    @Getter
    @NoArgsConstructor
    public static class OrderItem {
        @NotNull(message = "상품 ID는 필수입니다.")
        private Long productId;
        private Integer quantity;
    }
}