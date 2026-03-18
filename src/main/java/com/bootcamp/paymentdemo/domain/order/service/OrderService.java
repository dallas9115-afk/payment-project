package com.bootcamp.paymentdemo.domain.order.service;


import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.customer.repository.CustomerRepository;
import com.bootcamp.paymentdemo.domain.order.dto.Request.CreateOrderRequest;
import com.bootcamp.paymentdemo.domain.order.dto.Response.CreateOrderResponse;
import com.bootcamp.paymentdemo.domain.order.dto.Response.OrderDetailResponse;
import com.bootcamp.paymentdemo.domain.order.dto.Response.OrderListResponse;
import com.bootcamp.paymentdemo.domain.order.entity.Order;
import com.bootcamp.paymentdemo.domain.order.entity.OrderItem;
import com.bootcamp.paymentdemo.domain.order.repository.OrderItemRepository;
import com.bootcamp.paymentdemo.domain.order.repository.OrderRepository;
import com.bootcamp.paymentdemo.domain.product.entity.Product;
import com.bootcamp.paymentdemo.domain.product.repository.ProductRepository;
import com.github.f4b6a3.tsid.TsidCreator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;



//  주문 생성
    @Transactional
    public CreateOrderResponse save(Long customerId,CreateOrderRequest request) {
        // 1. 고객 조회
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("고객 없음"));

        // 2. 주문 아이디, 주문 번호 TSID, UUID 활용.
        String orderId = generateOrderId();
        String orderNumber = generateOrderNumber();

        Order order=new Order(orderId,customer,orderNumber);
        Order savedOrder = orderRepository.save(order);

        int totalAmount = 0;
        List<OrderItem> orderItems = new ArrayList<>();

        // 프론트엔드 요청 규격상 주문상품 목록이 request 안의 items에 담겨서 들어옴
        // 따라서 request.getItems()를 순회하면서 상품별 주문 정보를 처리한다.
        for (CreateOrderRequest.OrderItem itemRequest : request.getItems()) {


            // 프론트가 보낸 productId로 실제 상품이 존재하는지 조회
            // 존재하지 않으면 예외 발생
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "없는 상품입니다. id는 " + itemRequest.getProductId()
                    ));

            OrderItem orderItem = new OrderItem(savedOrder, product, itemRequest.getQuantity());
            orderItems.add(orderItem);

            totalAmount += orderItem.getProductPrice() * orderItem.getQuantity();
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
    public OrderDetailResponse getorderdetail(String orderId) {
        // 1. 주문 조회
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문 없다."));

        // 2. 주문 상품 조회
        List<OrderItem> orderItems=orderItemRepository.findByOrder(order);

        return new OrderDetailResponse(
                order.getOrderNumber(),
                order.getOrderId(),
                order.getTotalAmount(),
                order.getTotalAmount(),
                order.getStatus().name(),
                orderItems
        );
    }

    // 주문 목록 조회
    @Transactional(readOnly = true)
    public List<OrderListResponse> getorderlist(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("고객 없음"));

        List<Order> orders = orderRepository.findByCustomer(customer);
        List<OrderListResponse> responseList = new ArrayList<>();

        for (Order order : orders) {
            OrderListResponse response = new OrderListResponse(
                    order.getOrderNumber(),
                    order.getOrderId(),
                    order.getTotalAmount(),   // 수정 필요. total
                    order.getTotalAmount(),   // 수정 필요.final
                    order.getStatus().name(),
                    order.getCreatedAt()
            );

            responseList.add(response);
        }

        return responseList;
    }

    // orderid tsid활용. 생성 시간에 따라 생성됨. String.
    private String generateOrderId() {
        return "OID-" + TsidCreator.getTsid().toString();
    }

    // 주문 번호 생성 로직 (보안 및 식별력 강화), String.
    private String generateOrderNumber(){
        String dataPrefix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddhhmmss"));
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "ORD-" + dataPrefix + "-" + randomSuffix;
    }


}

