package com.bootcamp.paymentdemo.domain.product.service;

import com.bootcamp.paymentdemo.domain.order.entity.Order;
import com.bootcamp.paymentdemo.domain.order.entity.OrderItem;
import com.bootcamp.paymentdemo.domain.order.entity.OrderStatus;
import com.bootcamp.paymentdemo.domain.order.repository.OrderItemRepository;
import com.bootcamp.paymentdemo.domain.order.repository.OrderRepository;
import com.bootcamp.paymentdemo.domain.product.dto.Response.ProductOneResponse;
import com.bootcamp.paymentdemo.domain.product.entity.Product;
import com.bootcamp.paymentdemo.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class ProductService {

    public final ProductRepository productRepository;
    public final OrderItemRepository orderItemRepository;
    public final OrderRepository orderRepository;

    // 단건 조회
    @Transactional(readOnly = true)
    public ProductOneResponse productGetOne(Long productId){
        Product product =productRepository.findById(productId).orElseThrow(
                ()-> new IllegalArgumentException("해당 상품이 없습니다.")
        );
        return new ProductOneResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStock(),
                product.getDescription(),
                product.getStatus(),
                product.getCategory()
        );
    }

    // 모두 조회
    @Transactional(readOnly = true)
    public List<ProductOneResponse> productGetAll() {
        List<Product> products=productRepository.findAll();

        List<ProductOneResponse> dtos=new ArrayList<>();
        for(Product product: products){
            ProductOneResponse dto=new ProductOneResponse(
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    product.getStock(),
                    product.getDescription(),
                    product.getStatus(),
                    product.getCategory()
            );
            dtos.add(dto);
        }
        return dtos;
    }


    // 결제 성공 -> 재고 차감.
    @Transactional
    public void decreaseStockByOrder(Long orderId) {
        List<OrderItem> orderItems = orderItemRepository.findAllByOrder_Id(orderId);

        if (orderItems.isEmpty()) {
            throw new IllegalArgumentException("주문상품이 존재하지 않습니다. orderId=" + orderId);
        }
        String errorMessage = "";
        for (OrderItem orderItem : orderItems) {
            Product product = orderItem.getProduct();
            int quantity = orderItem.getQuantity();

            if (product.getStock() < quantity) {
                errorMessage+=
                        "\n 재고가 부족합니다. 상품명=" + product.getName()
                                + ", 현재 재고=" + product.getStock()
                                + ", 요청 수량=" + quantity;

            }
        }
        // 에러 메시지 한번에 모아서 던지기.
        if(!errorMessage.isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }

        for (OrderItem orderItem : orderItems) {
            Product product = orderItem.getProduct();
            product.decreaseStock(orderItem.getQuantity());
        }
    }


    // 환불 -> 재고 증가.
    @Transactional
    public void restoreStockByOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다. id=" + orderId));

        // 1) 이미 취소된 주문이면 멱등 처리
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }

        // 2) 결제 완료 상태인 주문만 재고 복구 가능
        if (order.getStatus() != OrderStatus.PAID) {
            throw new IllegalStateException("재고 복구가 불가능한 주문 상태입니다. 현재 상태는" + order.getStatus());
        }

        List<OrderItem> orderItems = orderItemRepository.findAllByOrder_Id(orderId);

        if (orderItems.isEmpty()) {
            throw new IllegalArgumentException("주문상품이 존재하지 않습니다. orderId=" + orderId);
        }

        for (OrderItem orderItem : orderItems) {
            Product product = orderItem.getProduct();
            product.increaseStock(orderItem.getQuantity());
        }
    }

}
