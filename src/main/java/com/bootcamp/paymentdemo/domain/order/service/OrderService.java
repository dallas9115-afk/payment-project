package com.bootcamp.paymentdemo.domain.order.service;


import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.customer.repository.CustomerRepository;
import com.bootcamp.paymentdemo.domain.order.dto.Request.CreateOrderRequest;
import com.bootcamp.paymentdemo.domain.order.dto.Response.CreateOrderResponse;
import com.bootcamp.paymentdemo.domain.order.dto.Response.OrderDetailResponse;
import com.bootcamp.paymentdemo.domain.order.dto.Response.OrderDetailListResponse;
import com.bootcamp.paymentdemo.domain.order.dto.Response.OrderListResponse;
import com.bootcamp.paymentdemo.domain.order.entity.Order;
import com.bootcamp.paymentdemo.domain.order.entity.OrderItem;
import com.bootcamp.paymentdemo.domain.order.entity.OrderStatus;
import com.bootcamp.paymentdemo.domain.order.repository.OrderItemRepository;
import com.bootcamp.paymentdemo.domain.order.repository.OrderRepository;
import com.bootcamp.paymentdemo.domain.payment.entity.Payment;
import com.bootcamp.paymentdemo.domain.payment.repository.PaymentRepository;
import com.bootcamp.paymentdemo.domain.point.entity.PointHistory;
import com.bootcamp.paymentdemo.domain.point.entity.PointType;
import com.bootcamp.paymentdemo.domain.point.repository.PointHistoryRepository;
import com.bootcamp.paymentdemo.domain.product.entity.Product;
import com.bootcamp.paymentdemo.domain.product.repository.ProductRepository;
import com.bootcamp.paymentdemo.global.error.CommonError;
import com.bootcamp.paymentdemo.global.error.CommonException;
import com.github.f4b6a3.tsid.TsidCreator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.apache.logging.log4j.ThreadContext.isEmpty;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final PointHistoryRepository pointHistoryRepository;


    //  주문 생성
    @Transactional
    public CreateOrderResponse save(Long customerId,CreateOrderRequest request) {
        // 1. 고객 조회
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CommonException(CommonError.CUSTOMER_NOT_FOUND));

        // 2. 주문 아이디, 주문 번호 TSID, UUID 활용.
        String orderId = generateOrderId();
        String orderNumber = generateOrderNumber();

        Order order=new Order(orderId,customer,orderNumber);
        Order savedOrder = orderRepository.save(order);

        int totalAmount = 0;
        List<OrderItem> orderItems = new ArrayList<>();

        // 프론트엔드 요청 규격상 주문상품 목록이 request 안의 items에 담겨서 들어옴
        // 따라서 request.getItems()를 순회하면서 상품별 주문 정보를 처리한다.
        String errorMessage = "";
        for (CreateOrderRequest.OrderItem itemRequest : request.getItems()) {

            // 프론트가 보낸 productId로 실제 상품이 존재하는지 조회
            // 존재하지 않으면 예외 발생
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "없는 상품입니다. 이 id는 " + itemRequest.getProductId()
                    ));

            // 재고 부족 체크
            if (product.getStock() < itemRequest.getQuantity()) {
                errorMessage+= "\n 재고가 부족합니다. 상품명: " + product.getName()
                                + ", 현재 재고: " + product.getStock()
                                + ", 요청 수량: " + itemRequest.getQuantity();

            }

            OrderItem orderItem = new OrderItem(savedOrder, product, itemRequest.getQuantity());
            orderItems.add(orderItem);

            totalAmount += orderItem.getProductPrice() * orderItem.getQuantity();
        }

        // 에러 메시지 한번에 모아서 던지기.
        if(!errorMessage.isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }

        orderItemRepository.saveAll(orderItems);
        savedOrder.changeTotalAmount(totalAmount);

        return new CreateOrderResponse(
                savedOrder.getOrderId(),
                savedOrder.getTotalAmount(),
                savedOrder.getOrderNumber()
        );

    }


    // 주문 상세 조회
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetail(String orderId,Long productId) {
        // 1. 주문 조회
        OrderItem orderItem = orderItemRepository.findByOrderOrderIdAndProductId(orderId,productId)
                .orElseThrow(() -> new IllegalArgumentException("주문이 없습니다."));

        return new OrderDetailResponse(
                orderItem.getOrder().getOrderNumber(),
                orderItem.getOrder().getOrderId(),
                orderItem.getProduct().getId(),
                orderItem.getProduct().getName(),
                orderItem.getProductPrice(),
                orderItem.getQuantity(),
                orderItem.getProductPrice() * orderItem.getQuantity()
        );
    }

    // 주문 상품 목록 리스트 조회
    @Transactional(readOnly = true)
    public List<OrderDetailListResponse> getOrderListDetail(String orderId) {
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderOrderId(orderId);

        if (orderItems.isEmpty()) {
            throw new IllegalArgumentException("주문상품이 없습니다.");
        }

        List<OrderDetailListResponse> responseList = new ArrayList<>();

        for (OrderItem orderItem : orderItems) {
            OrderDetailListResponse response = new OrderDetailListResponse(
                    orderItem.getProduct().getId(),
                    orderItem.getProductName(),
                    orderItem.getProductPrice(),
                    orderItem.getQuantity(),
                    orderItem.getProductPrice() * orderItem.getQuantity()
            );
            responseList.add(response);
        }

        return responseList;
    }

    // 주문 목록 조회
    @Transactional(readOnly = true)
    public List<OrderListResponse> getOrderList(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("고객이 없습니다."));


        List<Order> orders = orderRepository.findByCustomer(customer);

        if (orders.isEmpty()) {
            throw new IllegalArgumentException("주문 내역이 없습니다.");
        }

        List<OrderListResponse> responseList = new ArrayList<>();

        for (Order order : orders) {
            Payment payment=paymentRepository.findByOrder(order)
                    .orElseThrow(()-> new IllegalArgumentException("해당 주문의 결제 정보가 없습니다"));

            // 적립, 사용한 포인트 0으로 선언.
            Integer usedPoints = 0;
            Integer finalAmount = order.getTotalAmount();
            Integer earnedPoints = 0;

            // 타입을 맞추기 위해서 intValue(); 사용해서 맞춰준다.
            usedPoints = payment.getUsePoint().intValue();
            finalAmount = payment.getPgAmount().intValue();

            List<PointHistory> pointHistories =
                    pointHistoryRepository.findAllByOrderIdAndType(order.getOrderId(), PointType.EARNED);

            for (PointHistory pointHistory : pointHistories) {
                earnedPoints += pointHistory.getAmount().intValue();
            }


            OrderListResponse response = new OrderListResponse(
                    order.getOrderNumber(),
                    order.getOrderId(),
                    order.getTotalAmount(),   // 주문 금액(포인트 차감전)
                    usedPoints,               // 사용한 포인트
                    finalAmount,              // 최종 결제 금액
                    earnedPoints,             // 적립된 포인트
                    "KRW",                    // 통화
                    order.getStatus().name(),
                    order.getCreatedAt()
            );

            responseList.add(response);
        }

        return responseList;
    }

    // orderId tsid활용. 생성 시간에 따라 생성됨. String.
    private String generateOrderId() {
        return "OID-" + TsidCreator.getTsid().toString();
    }

    // 주문 번호 생성 로직 (보안 및 식별력 강화), String.
    private String generateOrderNumber(){
        String dataPrefix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddhhmmss"));
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "ORD-" + dataPrefix + "-" + randomSuffix;
    }

 // 생성 → 결제 대기 → [결제 성공] → 주문 완료
    @Transactional
    public void completeOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다. id=" + orderId));

        if (order.getStatus() == OrderStatus.PENDING) {
            order.changeStatus(OrderStatus.PAID);
            return;
        }

        if (order.getStatus() == OrderStatus.PAID) {
            return; // 멱등 처리 첫 번째 호출: PENDING -> PAID 성공
                   //          두 번째 호출: 이미 성공한 상태 확인 → 그냥 종료
        }

        throw new IllegalStateException("주문 완료 처리가 불가능한 상태입니다. 현재상태는=" + order.getStatus());
    }


// 생성 -> 결제 대기 -> [결제 실패] → 결제 대기 (유지)
    @Transactional
    public void failOrderPayment(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다. id=" + orderId));

        if (order.getStatus() == OrderStatus.PENDING) {
            return; // 이미 결제 대기 상태 -> 유지
        }

        if (order.getStatus() == OrderStatus.PAID) {
            throw new IllegalStateException("이미 결제 완료된 주문은 결제 실패로 처리할 수 없습니다.");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("취소된 주문은 결제 실패 처리 대상이 아닙니다.");
        }
    }

    // 생성 -> 결제 대기 ->  [결제 성공] → [환불 요청] → 환불
    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다. id=" + orderId));

        // 1) 이미 취소된 주문이면 멱등 처리
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }

        // 2) 취소 가능한 상태만 취소 처리
        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.PENDING) {
            order.changeStatus(OrderStatus.CANCELLED);
            return;
        }

        // 3) 그 외 상태는 정책상 취소 불가로 보고 예외 전파
        throw new IllegalStateException("주문 취소가 불가능한 상태입니다. 현재상태는=" + order.getStatus());
    }

}
