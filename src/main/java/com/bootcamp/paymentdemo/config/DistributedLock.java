package com.bootcamp.paymentdemo.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Redis 분산락을 적용할 메서드에 붙이는 어노테이션
 *
 * key:
 * - SpEL 표현식으로 작성합니다.
 * - 예: "'lock:payment:confirm:' + #paymentId"
 *
 * waitTime:
 * - 락을 얻기 위해 기다릴 최대 시간(초)
 *
 * leaseTime:
 * - 락을 잡은 뒤 자동으로 만료될 시간(초)
 * - unlock이 누락되더라도 영구 잠금이 되지 않도록 하는 안전장치입니다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    String key();

    long waitTime() default 3L;

    long leaseTime() default 10L;
}
