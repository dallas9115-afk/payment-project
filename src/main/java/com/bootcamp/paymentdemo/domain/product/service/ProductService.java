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

import java.util.*;

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

        // 같은 순서로 잠가야 하기때문에 정렬해서 잠구기.
        orderItems.sort(Comparator.comparing(orderItem -> orderItem.getProduct().getId()));


        // LinkedHashMap 사용. key-value 형태로 저장한다. 또한 삽입 순서를 유지하기 때문이다.
       // 왜냐면 중복으로 주문이 들어왔을때 List는 처리가 불가능하기 때문이다.
        Map<Long, Product> lockedProducts = new LinkedHashMap<>();


        // 1. 동일 상품은 수량 합산 가능,
        // 2. 다른 상품이어도 각각 정상적으로 따로 처리.
        // ex) 상품 1, 수량 2           상품 1, 수량 2
        //     상품 2, 수량 3           상품 2, 수량 3
        //     상품 3, 수량 1           상품 1, 수량 4
        //     1 -> 2                  1 -> 6
        //     2 -> 3                  2 -> 3
        //     3 -> 1
        Map<Long, Integer> totalQuantity = new LinkedHashMap<>();

        // stockErrorMessage 재고 부족 검증 중 생긴 에러 메시지를 누적하는 변수
        String stockErrorMessage = "";

        // notFoundErrorMessage 상품 존재 에러 메시지를 누적하는 변수
        String notFoundProductMessage = "";


        for (OrderItem orderItem : orderItems) {
            Long productId = orderItem.getProduct().getId();
            int quantity = orderItem.getQuantity();

            Product product = lockedProducts.get(productId);

            // product == null인 이유. lockedProducts에 아직 이 productId의 상품이 없음
            //즉, 아직 한 번도 조회 안 했다는 뜻임.
            if (product == null) {
                Optional<Product> optionalProduct = productRepository.findByIdWithLock(productId);

                if (optionalProduct.isEmpty()) {
                    notFoundProductMessage += "\n해당 상품이 없습니다. productId=" + productId;
                    continue;
                }

                product = optionalProduct.get();
                lockedProducts.put(productId, product);
            }

            totalQuantity.put(productId,
                    totalQuantity.getOrDefault(productId, 0) + quantity);
        }

        // 에러 메시지 한번에 모아서 던지기. 상품 존재 에러 메시지를 누적하는 변수
        if (!notFoundProductMessage.isEmpty()) {
            throw new IllegalArgumentException(notFoundProductMessage);
        }


        // totalQuantity 맵에 들어있는 key-value 값을 하나씩 꺼내서 반복. 밑에 방식으로 들어가 있음.
        //  entrySet()은 Map 안에 들어있는 모든 key-value 쌍을 꺼내는 메서드이다.
        //        ex) 상품 1, 수량 2          상품 1, 수량 2
        //        //    상품 2, 수량 3           상품 2, 수량 3
        //        //    상품 3, 수량 1           상품 1, 수량 4
        //        //     1 ->2                   1 -> 6
        //        //     2 -> 3                  2 -> 3
        //        //     3 -> 1
        for (Map.Entry<Long, Integer> entry : totalQuantity.entrySet()) {
            Long productId = entry.getKey();
            int totalQuanty = entry.getValue();
            Product product = lockedProducts.get(productId);

            if (product.getStock() < totalQuanty ) {
                stockErrorMessage +=
                        "\n재고가 부족합니다. 상품명=" + product.getName()
                                + ", 현재 재고=" + product.getStock()
                                + ", 요청 수량=" + totalQuanty;
            }
        }
        // 에러 메시지 한번에 모아서 던지기.
        if(!stockErrorMessage.isEmpty()) {
            throw new IllegalArgumentException(stockErrorMessage);
        }


        for (Map.Entry<Long, Integer> entry : totalQuantity.entrySet()) {
            Product product = lockedProducts.get(entry.getKey());
            product.decreaseStock(entry.getValue());
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

        // 같은 순서로 잠가야 하기때문에 정렬해서 잠구기.
        orderItems.sort(Comparator.comparing(orderItem -> orderItem.getProduct().getId()));


        for (OrderItem orderItem : orderItems) {
            Product product = productRepository.findByIdWithLock(orderItem.getProduct().getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "해당 상품이 없습니다. productId=" + orderItem.getProduct().getId()
                    ));
            product.increaseStock(orderItem.getQuantity());
        }
    }

}
