### 1. 코드 리뷰: 프로젝트의 기술적 장점 및 특징 분석

**① 외부 결제 시스템(PortOne) 연동의 견고함과 멱등성(Idempotency) 보장**
* **특징:** `PortOneApiClient`를 통한 외부 PG사 연동 시, 단순 API 호출에 그치지 않고 네트워크 타임아웃이나 재시도 상황을 고려한 멱등키(Idempotency-Key)를 HTTP 헤더에 주입하여 통신하고 있습니다.
* **장점:** 외부 시스템 장애나 클라이언트의 중복 요청(따닥)이 발생하더라도 이중 과금(Double Charge)을 원천적으로 차단하는 실무 수준의 결제 안정성을 확보했습니다. 또한 웹훅(`SubscriptionWebhookController`)을 연동하여 비동기 결제 결과를 안전하게 검증 및 반영하는 구조가 돋보입니다.

**② Redisson 분산 락을 활용한 완벽한 동시성(Concurrency) 제어**
* **특징:** `DistributedLock` 커스텀 어노테이션과 AOP(`DistributedLockAspect`), 그리고 `RedissonClient`를 활용하여 구독, 결제, 포인트 차감 로직에 분산 락을 적용했습니다.
* **장점:** 단일 서버의 `@Transactional`이나 DB 락(Optimistic Lock)이 가지는 한계를 넘어, 다중 서버(Scale-out) 환경에서도 동일 고객의 중복 결제나 포인트 초과 사용(Race Condition)을 완벽하게 방어하는 고도화된 동시성 제어 아키텍처를 구현했습니다.

**③ 복잡한 비즈니스 상태 생명주기(Lifecycle) 관리**
* **특징:** 구독(`SubscriptionStatus`), 결제(`PaymentStatus`), 환불(`RefundStatus`) 등 각 도메인별 상태 전이를 명확한 Enum으로 관리하며, `SubscriptionScheduler`, `BillingScheduler` 등을 통해 정기 결제 및 연체(Past Due), 유예 기간 만료 로직을 자동화했습니다.
* **장점:** 비즈니스 요구사항이 복잡한 정기구독 및 포인트 소멸 배치 로직을 서비스 레이어에 응집력 있게 구현하여 유지보수성을 극대화했습니다.

**④ 보안(OAuth2 + JWT) 및 전역 예외/응답 표준화**
* **특징:** OAuth2 로그인 성공 시 내부 `Customer` 엔티티로 매핑하여 JWT 토큰을 발급하는 정석적인 하이브리드 인증 구조를 가졌습니다. 또한 `GlobalExceptionHandler`와 `ApiResponse`를 통해 성공/실패 응답 포맷을 완벽하게 통일했습니다.
* **장점:** 프론트엔드 클라이언트와의 API 연동 규격을 명확히 하여 협업 효율을 높였으며, 보안 필터단부터 비즈니스 로직까지 일관된 예외 추적이 가능합니다.

---

### 2. README.md

```markdown
![java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring](https://img.shields.io/badge/Spring-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![mySQL](https://img.shields.io/badge/MySQL-00000F?style=for-the-badge&logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![JSON WEB TOKEN](https://img.shields.io/badge/json%20web%20tokens-323330?style=for-the-badge&logo=json-web-tokens&logoColor=pink)

---

# 🏢 Sparta Payment & Subscription System

> Spring Boot 기반 이커머스 결제/구독/포인트 통합 코어 시스템
> JWT + OAuth2 + Redisson 분산 락 + PortOne 결제 연동 + 웹훅 처리가 적용된 실무형 아키텍처 팀 프로젝트

---

# 📌 프로젝트 개요

본 프로젝트는 이커머스 서비스의 핵심인 **결제, 정기 구독, 포인트 시스템**을 다루는 백엔드 코어 서버입니다.

단순 CRUD가 아닌, 실제 대규모 결제 트래픽 및 외부망 연동 환경을 고려한 다음 설계 요소를 포함합니다.

* JWT 기반 커스텀 인증 및 OAuth2 (Google/Kakao) 소셜 로그인 연동
* 외부 PG사(PortOne) SDK 및 API 연동 (빌링키 발급, 단건/정기 결제, 환불)
* 결제 Webhook 비동기 수신 및 멱등성(Idempotency-Key) 검증
* Redisson 분산 락 기반 결제 및 포인트 동시성 제어 (Race Condition 방어)
* Spring Scheduler 기반 정기 구독 자동 청구 및 연체/유예 상태 관리
* 포인트 적립/차감 및 기간 만료 배치 시스템
* 공통 API 응답 포맷 통일 및 Global Exception Handler 적용

---

# 🚀 실행 방법 (Quick Start)

## 1️⃣ 환경 요구사항

* Java 17
* Gradle
* MySQL 8.x
* Redis (분산 락 및 세션 캐싱용)

---

## 2️⃣ 환경 변수 설정

보안을 위해 `application.yml`의 민감 정보는 OS 환경 변수 또는 GitHub Secrets 주입으로 구성되어 있습니다.
실행 전 로컬 환경 변수 또는 `src/main/resources/application-local.yml`에 아래 값들을 설정해주세요.

```yaml
DB_HOST=
DB_PORT=3306
DB_NAME=payment
DB_USER=
DB_PASSWORD=
REDIS_HOST=
REDIS_PASSWORD=
JWT_SECRET_KEY=
PORTONE_API_SECRET=
PORTONE_STORE_ID=
KAKAO_CLIENT_ID=
GOOGLE_CLIENT_ID=
```

> JWT_SECRET_KEY는 256비트 이상의 충분히 긴 랜덤 문자열 사용을 권장합니다.

---

## 3️⃣ DB 생성

```sql
CREATE DATABASE payment DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

## 4️⃣ 실행

```bash
./gradlew clean build
./gradlew bootRun
```

---

# 📌 API 목록 테이블 (도메인별 전체 정리)

<details>
<summary>👉 클릭해서 펼치기</summary>

<br>

---

# 🔐 AUTH 도메인

| Method | Endpoint | 설명 | 인증 필요 |
|--------|----------|------|------------|
| POST | /api/auth/v1/signup | 일반 회원가입 | ❌ |
| POST | /api/auth/v1/login | 일반 이메일 로그인 (JWT 발급) | ❌ |
| POST | /api/auth/v1/logout | 로그아웃 (토큰 무효화) | ✅ |
| GET | /oauth2/authorization/kakao | 카카오 소셜 로그인 연동 | ❌ |
| GET | /oauth2/authorization/google | 구글 소셜 로그인 연동 | ❌ |

---

# 👤 CUSTOMER 도메인

| Method | Endpoint | 설명 | 인증 필요 |
|--------|----------|------|------------|
| GET | /api/customer/v1/me | 내 프로필 및 멤버십 등급 조회 | ✅ |
| PUT | /api/customer/v1/me | 고객 정보 수정 | ✅ |
| GET | /api/customer/v1/membership/policy | 멤버십 등급 정책 조회 | ❌ |

---

# 🛍 PRODUCT & ORDER 도메인

| Method | Endpoint | 설명 | 인증 필요 |
|--------|----------|------|------------|
| GET | /api/product/v1/products | 상품 목록 조회 (페이징) | ❌ |
| GET | /api/product/v1/products/{id} | 상품 상세 조회 | ❌ |
| POST | /api/order/v1/orders | 주문서 생성 (다품목) | ✅ |
| GET | /api/order/v1/orders/{orderId} | 주문 상세 조회 | ✅ |
| PATCH | /api/order/v1/orders/{orderId}/cancel | 주문 취소 | ✅ |

---

# 💳 PAYMENT 도메인

| Method | Endpoint | 설명 | 인증 필요 |
|--------|----------|------|------------|
| POST | /api/payment/v1/methods | 결제 수단(빌링키) 등록 | ✅ |
| GET | /api/payment/v1/methods | 등록된 결제 수단 목록 조회 | ✅ |
| POST | /api/payment/v1/payments/ready | 결제 사전 준비 (위변조 방지) | ✅ |
| GET | /api/payment/v1/payments/{paymentId} | 결제 단건 상세 조회 | ✅ |
| POST | /api/payment/v1/webhook/portone | PortOne 결제 결과 웹훅 수신 | ❌ (IP/Secret 검증) |

---

# 🔄 SUBSCRIPTION 도메인

| Method | Endpoint | 설명 | 인증 필요 |
|--------|----------|------|------------|
| GET | /api/subscription/v1/plans | 활성화된 구독 플랜 목록 조회 | ❌ |
| POST | /api/subscription/v1/subscriptions | 정기 구독 시작 및 첫 결제 진행 | ✅ |
| GET | /api/subscription/v1/subscriptions/me | 내 구독 상태 및 다음 결제일 조회 | ✅ |
| GET | /api/subscription/v1/subscriptions/{id}/history | 특정 구독의 청구(결제) 내역 조회 | ✅ |
| PATCH | /api/subscription/v1/subscriptions/{id} | 구독 해지 및 빌링키 삭제 요청 | ✅ |

---

# 💰 POINT 도메인

| Method | Endpoint | 설명 | 인증 필요 |
|--------|----------|------|------------|
| GET | /api/point/v1/balance | 현재 보유 포인트 잔액 조회 | ✅ |
| GET | /api/point/v1/history | 포인트 적립/차감 내역 조회 | ✅ |
| POST | /api/point/v1/use | 포인트 차감 (주문 결제 시 사용) | ✅ |

---

# 💸 REFUND 도메인

| Method | Endpoint | 설명 | 인증 필요 |
|--------|----------|------|------------|
| POST | /api/refund/v1/requests | 결제 환불 요청 (전액/부분) | ✅ |
| GET | /api/refund/v1/requests/{refundId} | 환불 진행 상태 상세 조회 | ✅ |

</details>

# 📌 API 공통 규격

### 1) 인증/인가

- 인증 방식: **JWT (Bearer Token)**
- 보호된 API 호출 시 HTTP Header 포함 필수

```http
Authorization: Bearer {accessToken}
```

### 2) 공통 성공 응답 형식 (`ApiResponse<T>`)

```json
{
  "success": true,
  "data": {
    // 응답 데이터
  },
  "error": null
}
```

### 3) 공통 실패 응답 형식

```json
{
  "success": false,
  "data": null,
  "error": {
    "timestamp": "2026-03-26T10:00:00",
    "status": 400,
    "error": "Bad Request",
    "code": "INVALID_PARAMETER",
    "message": "결제 금액이 일치하지 않습니다.",
    "path": "/api/payment/v1/webhook"
  }
}
```

---

# 🏗 핵심 시스템 아키텍처 및 기술 전략

## 1. 분산 락(Distributed Lock) 기반 동시성 제어

결제 승인, 정기 구독 청구, 포인트 차감 시 사용자의 중복 클릭이나 외부망 타임아웃 재시도로 인한 다중 스레드 접근이 발생할 수 있습니다.
* **해결:** `@DistributedLock` 커스텀 AOP와 **Redisson**을 도입하여 `customerId` 기반으로 락을 점유합니다.
* **효과:** 다중 인스턴스 서버 환경에서도 동일 회원의 이중 결제나 포인트 마이너스 잔고 발생을 원천 차단합니다.

## 2. 멱등성(Idempotency) 보장 아키텍처

외부 PG사(PortOne) API 통신 중 서버가 다운되거나 타임아웃이 발생하여 재시도 로직이 동작할 수 있습니다.
* **해결:** 주문 및 구독 청구(Billing) 시점마다 고유 식별자와 조합된 `Idempotency-Key`를 HTTP Header에 삽입하여 PortOne 서버로 전송합니다.
* **효과:** 동일한 결제 요청이 수차례 도달하더라도 PG사 측에서 단 1회의 승인만 이루어지도록 보장하여 데이터 정합성과 신뢰성을 확보했습니다.

## 3. 안정적인 정기 구독(Subscription) 상태 기계(State Machine)

단순한 상태값을 넘어 정기 구독 특화 라이프사이클을 설계했습니다.
* `PENDING`(대기) -> `ACTIVE`(정상 구독) -> `PAST_DUE`(연체/유예) -> `ENDED`(만료) -> `CANCELED`(해지)
* **스케줄러 자동화:** Spring Batch/Scheduler를 통해 매일 자정에 `getNextBillingDate()`를 확인하여 결제를 자동 청구(`READY` -> `SUCCESS` or `FAILED`)하고, 연체 시 유예 기간(Grace Period)을 계산하여 자동으로 정지 처리합니다.

## 4. 이벤트 및 웹훅(Webhook) 기반 비동기 결제 검증

프론트엔드의 결제 완료 호출만을 신뢰하지 않습니다.
* **보안 검증:** 고객이 결제를 시도하면 서버에 `READY` 장부를 먼저 만들고, 결제가 완료되면 PortOne이 서버망으로 직접 쏘아주는 **Webhook**을 수신합니다.
* **위변조 차단:** 웹훅으로 들어온 결제건에 대해 다시 PortOne API 단건 조회를 수행하여, 악의적인 사용자가 브라우저에서 조작한 결제 금액과 실제 DB의 청구 금액이 일치하는지 2차 검증(Payment Validation)을 수행한 후 `ACTIVE` 처리합니다.

## 5. 확장 가능한 엔티티 및 Soft Delete 설계

* `BaseEntity`를 통한 생성일/수정일 자동 관리
* 포인트 이력, 결제 내역, 주문 내역 등 금융과 관련된 모든 주요 데이터는 물리적 삭제(DELETE)를 수행하지 않고 논리 삭제(Soft Delete) 또는 취소(Cancel) 트랜잭션 기록을 남겨 강력한 회계 감사(Audit) 요건을 충족합니다.

---

# 🧠 학습 및 구현 포인트

* JWT 구조 설계 및 OAuth2 Principal 매핑
* Spring AOP를 활용한 외부 인프라(Redis) 제어 및 관심사 분리
* 결제 시스템에서의 멱등성(Idempotency)의 중요성 및 실전 적용
* 비동기 결제 웹훅 처리와 데이터 위변조 방어 전략
* 복잡한 구독 비즈니스 로직의 트랜잭션 단위 분할 및 순환 참조(Circular Reference) 해결
* 객체 지향적인 상태 전이(State Pattern)를 활용한 도메인 모델링
