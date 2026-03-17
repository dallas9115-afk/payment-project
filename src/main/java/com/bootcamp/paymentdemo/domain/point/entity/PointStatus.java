package com.bootcamp.paymentdemo.domain.point.entity;

public enum PointStatus {
    // 적립(미사용) - 포인트가 처음 생성되었을때
    ACCUMULATED("적립"),
    // 부분 사용중 - 잔액이 남아있지만 일부 차감이 발생했을 때
    PARTIALLY_USED("사용"),
    // 사용완료 - remainAmount가 0이 되었을 때
    COMPLETED("완료"),
    // 만료소멸 - 유효기간이 지나 잔액이 사라졌을 때
    EXPIRED("만료"),
    // 취소 - 포인트 사용 취소
    CANCELED("취소");

    private final String description;
    PointStatus(String description) {
        this.description = description;
    }
}
