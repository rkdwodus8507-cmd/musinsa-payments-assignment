# 무료 포인트 시스템 (API)

무신사페이먼츠 Backend Engineer 과제. 무료 포인트의 **적립 / 적립취소 / 사용 / 사용취소**를 제공하는 REST API입니다.

포인트는 금전과 동일하게 다뤄야 하므로, 이 구현의 최우선 목표는 **어떤 시점에도 잔액과 원장이 어긋나지 않는 것**입니다. 그 목표에서 나온 설계 결정들을 아래 [핵심 설계 결정](#핵심-설계-결정)에 정리했습니다.

---

## 1. 개발 환경

| 항목 | 버전 |
|---|---|
| Java | 21 (Temurin) |
| Spring Boot | 3.5.16 |
| DB | H2 (in-memory) |
| Build | Gradle 8.14.3 (Kotlin DSL, wrapper 포함) |
| API 문서 | springdoc-openapi 2.8.9 (Swagger UI) |

---

## 2. 빌드 및 실행

별도 설치 없이 저장소에 포함된 Gradle wrapper로 실행됩니다. JDK 21만 있으면 됩니다.

```bash
./gradlew clean build
```

```bash
./gradlew bootRun
```

실행 후 접속 경로:

| | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI 스펙 | http://localhost:8080/v3/api-docs |
| H2 콘솔 | http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:point`, User: `sa`, 비밀번호 없음) |

테스트만 실행하려면:

```bash
./gradlew test
```

---

## 3. 요구사항 구현 현황

| # | 요구사항 | 구현 |
|---|---|---|
| 3-1-1 | 1회 적립 1P 이상 10만P 이하, **하드코딩 아닌 방법으로 제어** | `point_policy` 테이블 + 관리자 API. 초기값은 `application.yml` |
| 3-1-2 | 개인별 최대 보유금액 제한, **별도 방법으로 변경 가능** | 동일. `PUT /api/v1/admin/points/policies` 로 무중단 변경 |
| 3-1-3 | 적립분이 **어떤 주문에서 1원 단위로** 사용됐는지 추적 | `point_lot_usage` (적립분 × 사용거래 × 주문번호 × 금액) |
| 3-1-4 | 관리자 수기지급분을 다른 적립과 **구분 식별** | `point_lot.manual` + 전용 엔드포인트 `POST /api/v1/admin/points/earn` |
| 3-1-5 | 만료일 최소 1일 ~ **5년 미만**, 기본 365일 | 정책값(`min/max/defaultExpireDays`). 5년 미만은 시스템 불변식으로 상한 고정 |
| 3-2-1 | 적립 취소는 전액만, **일부라도 사용됐으면 불가** | `PointLot.cancelEarn()` 에서 `remaining == original` 검증 |
| 3-3-1 | 주문 시에만 사용 | 사용 API가 `orderId` 필수 |
| 3-3-2 | 사용 시 주문번호 기록 | `point_transaction.order_id` + `point_lot_usage.order_id` |
| 3-3-3 | **수기지급 우선 → 만료임박 순** 사용 | `ORDER BY manual DESC, expire_at ASC, id ASC` |
| 3-4-1 | 전체 또는 일부 사용취소 | 부분취소 누적 지원 (`canceled_amount`) |
| 3-4-2 | 취소 시점에 **이미 만료된 적립분은 신규적립 처리** | 복원 불가 시 새 EARN 거래 + 새 적립분 생성 |

과제 명세 4장의 예시(A~E 전체 흐름)는 [`PointScenarioTest`](src/test/java/com/musinsa/payments/point/PointScenarioTest.java) 에 그대로 재현되어 있습니다.

산출물:

- ERD — [`resource/erd.png`](resource/erd.png) ([SVG](resource/erd.svg))
- AWS 아키텍처 (옵션) — [`resource/aws-architecture.png`](resource/aws-architecture.png) ([SVG](resource/aws-architecture.svg))

---

## 4. 도메인 모델

![ERD](resource/erd.png)

| 테이블 | 역할 |
|---|---|
| `point_transaction` | **pointKey를 부여받는 모든 거래 이벤트**. EARN / EARN_CANCEL / USE / USE_CANCEL |
| `point_lot` | EARN 거래와 1:1. `remaining_amount`, `expire_at`, `manual`, `status` 보유. **잔액의 유일한 원천** |
| `point_lot_usage` | 사용 상세. "어떤 사용거래가 어떤 적립분을 어떤 주문에서 얼마 썼는가" — 1원 단위 추적의 핵심 |
| `point_lot_usage_cancel` | 사용취소 상세. 복원(`restored_lot_id`)과 만료 재적립(`reissued_lot_id`)을 구분 기록 |
| `point_wallet` | **잔액 컬럼 없음.** 사용자 단위 락(`SELECT ... FOR UPDATE`) 대상으로만 존재 |
| `point_policy` | 단일 행. 런타임에 변경 가능한 정책값 |

`point_lot.status` 는 `AVAILABLE` / `EXPIRED` / `CANCELED` 3가지입니다. 잔액이 0이 되는 것은 상태 전이가 아니라 `remaining_amount` 값 변화로만 표현해, 상태 머신을 단순하게 유지했습니다.

DDL과 인덱스는 [`src/main/resources/db/schema.sql`](src/main/resources/db/schema.sql) 에 있습니다.

---

## 5. 핵심 설계 결정

### 5-1. 잔액을 컬럼으로 저장하지 않는다

`point_wallet.balance` 같은 캐시 컬럼을 두지 않았습니다. 잔액은 항상 이렇게 계산합니다.

```sql
SELECT COALESCE(SUM(remaining_amount), 0)
FROM point_lot
WHERE user_id = ? AND status = 'AVAILABLE' AND expire_at > now()
```

잔액 컬럼을 두면 **만료 시점에 원장과 캐시가 반드시 어긋납니다.** 만료는 시각이 지나면 발생하는 사건인데 컬럼 차감은 배치가 돌아야 반영되므로, 배치가 1분만 늦어도 이미 만료된 포인트가 사용 가능한 것처럼 보입니다. 사용취소로 인한 재적립까지 겹치면 어긋남을 추적하기 어려워집니다.

계산식에 `expire_at > now()` 조건이 들어 있으므로 **만료 배치가 돌지 않아도 잔액과 사용 가능 여부는 항상 정확합니다.** 배치는 정합성을 만드는 주체가 아니라 상태를 정리하고 이력을 남기는 역할만 합니다.

트레이드오프는 명확합니다. 사용자당 적립분이 수만 건 규모가 되면 합산 비용이 커집니다. 그 시점에는 `(user_id, status, expire_at)` 커버링 인덱스로 버티고, 그래도 부족하면 잔액 스냅샷 테이블을 **원장에서 파생되는 캐시로** 도입하되 원장을 정본으로 유지하는 방향이 맞다고 봅니다.

### 5-2. `point_wallet` 은 잔액이 아니라 락을 위해 존재한다

동시 요청 시 한도 초과나 잔액 초과 사용을 막으려면 검사와 반영 사이가 원자적이어야 합니다. 그래서 적립·적립취소·사용·사용취소 진입부에서 항상 사용자 행을 먼저 잠급니다.

```java
walletLocker.lock(userId);   // SELECT ... FOR UPDATE
```

사용자 단위 락이므로 **서로 다른 사용자 간에는 경합이 없고**, 수평 확장에 지장을 주지 않습니다.

지갑 행이 아직 없는 최초 요청은 `MERGE INTO point_wallet ... KEY(user_id)` (upsert)로 처리합니다. 동일 사용자의 최초 요청이 동시에 들어오면 뒤에 온 트랜잭션이 앞선 트랜잭션의 커밋을 기다렸다가 기존 행을 잠그므로, 중첩 트랜잭션이나 유니크 제약 예외 처리 없이 직렬화됩니다.

> 처음에는 `REQUIRES_NEW` 중첩 트랜잭션으로 지갑을 생성했으나, 요청 하나가 커넥션 2개를 점유해 동시 요청이 풀 크기를 넘으면 교착에 빠졌습니다. 동시성 테스트에서 드러나 upsert 방식으로 교체했습니다. (`MERGE ... KEY` 는 H2 문법입니다. MySQL 로 옮긴다면 `INSERT ... ON DUPLICATE KEY UPDATE` 로 대체합니다.)

### 5-3. 정책값은 DB에 두고 API로 바꾼다

과제의 "**하드코딩이 아닌 별도의 방법으로 변경**할 수 있어야 한다"를 충족하기 위해, 정책을 `application.yml` 상수가 아니라 `point_policy` 테이블로 관리합니다. yml 값은 **최초 기동 시 시딩용 초기값**일 뿐이고, 운영 중 변경은 관리자 API로 합니다.

```bash
curl -X PUT http://localhost:8080/api/v1/admin/points/policies \
  -H 'Content-Type: application/json' \
  -d '{"minEarnAmount":1,"maxEarnAmount":200000,"maxUserBalance":1000000,
       "defaultExpireDays":365,"minExpireDays":1,"maxExpireDays":1824}'
```

재기동 없이 다음 요청부터 즉시 반영됩니다. yml `@ConfigurationProperties` 방식은 변경 시 재기동이 필요해 "변경 가능"이라는 요구를 온전히 만족하지 못한다고 판단했습니다.

`maxExpireDays` 상한은 **1824일**입니다. 과제의 "5년 미만"을 일 단위로 옮기면 1825일 미만이므로 1824일이 상한입니다. 이 상한 자체는 시스템 불변식이라 정책값으로 열지 않고 도메인에서 고정 검증합니다.

### 5-4. 사용 순서와 사용취소 순서

**사용**: `manual DESC, expire_at ASC, id ASC` — 수기지급분을 만료일과 무관하게 먼저 쓰고, 그다음 만료가 임박한 순서로 씁니다.

**사용취소**: 사용된 순서 그대로(`point_lot_usage.id ASC`) 되돌립니다. 과제 예시에서 1200원(A 1000 + B 200) 중 1100원을 취소하면 A 1000 전액과 B 100이 취소되어야 하는데, 이 순서가 그 결과와 일치합니다.

**만료된 적립분의 복원**: 복원 대상이 이미 만료됐다면 되살릴 수 없으므로 **새 EARN 거래와 새 적립분을 만듭니다**(예시의 pointKey E). 이때 만료일은 정책 기본값으로 새로 부여하고, `manual` 플래그는 원래 적립분에서 승계합니다. 수기지급분을 썼다가 취소했는데 일반 적립분으로 바뀌면 사용 우선순위가 달라져 사용자에게 불리하기 때문입니다.

### 5-5. 만료 처리

만료는 **조회 시점 판정**과 **배치 상태 전이**를 함께 씁니다.

- 잔액 계산·사용 가능 여부는 항상 `expire_at > now()` 로 실시간 판정 → 배치와 무관하게 정확
- 배치(`point.expiration.cron`, 기본 매일 04:00)는 지난 적립분을 `EXPIRED` 로 전이시켜 만료 이력을 남기고 조회 대상을 줄임

배치는 청크 단위로 **각각 독립 트랜잭션**에서 처리해 락 점유 시간을 짧게 유지합니다. 트랜잭션 경계를 프록시로 태워야 하므로 청크 처리는 `PointExpirationProcessor` 라는 별도 빈에 두었습니다(같은 클래스 내부 호출은 `@Transactional` 이 적용되지 않습니다).

수동 실행: `POST /api/v1/admin/points/expirations`

---

## 6. API

전체 명세는 Swagger UI에서 확인할 수 있습니다. 요약은 다음과 같습니다.

### 포인트

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/v1/points/earn` | 적립 |
| POST | `/api/v1/points/earn/{pointKey}/cancel` | 적립 취소 |
| POST | `/api/v1/points/use` | 사용 |
| POST | `/api/v1/points/use/{pointKey}/cancel` | 사용 취소 (부분 가능) |
| GET | `/api/v1/points/balance?userId=` | 잔액 및 적립분 목록 |
| GET | `/api/v1/points/transactions?userId=` | 거래 이력 (페이징) |
| GET | `/api/v1/points/orders/{orderId}/usages` | **주문별 1원 단위 사용 추적** |

### 관리자

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/v1/admin/points/earn` | 수기 지급 (`manual = true`) |
| GET | `/api/v1/admin/points/policies` | 정책 조회 |
| PUT | `/api/v1/admin/points/policies` | 정책 변경 |
| POST | `/api/v1/admin/points/expirations` | 만료 배치 수동 실행 |

### 요청/응답 예시

적립:

```bash
curl -X POST http://localhost:8080/api/v1/points/earn \
  -H 'Content-Type: application/json' \
  -d '{"userId":7,"amount":1000,"expireDays":30,"memo":"이벤트 적립"}'
```

```json
{
  "pointKey": "d634ab79-08a7-4289-9774-f843d3746365",
  "userId": 7, "amount": 1000, "manual": false,
  "expireAt": "2026-08-27T18:59:54.640373", "balance": 1000
}
```

사용 — 어떤 적립분에서 얼마씩 빠졌는지 응답에 포함됩니다.

```bash
curl -X POST http://localhost:8080/api/v1/points/use \
  -H 'Content-Type: application/json' \
  -d '{"userId":7,"orderId":"A1234","amount":1200}'
```

```json
{
  "pointKey": "b553be55-f131-42ff-91ef-9d92f15cae6e",
  "orderId": "A1234", "amount": 1200, "balance": 600,
  "details": [
    { "earnPointKey": "301ce53b-...", "amount": 300, "manual": true },
    { "earnPointKey": "d634ab79-...", "amount": 900, "manual": false }
  ]
}
```

수기지급분 300원이 만료일이 더 길었음에도 먼저 사용된 것을 확인할 수 있습니다.

사용취소 — 만료된 적립분은 `reissued: true` 로 표시되고 새 pointKey가 발급됩니다.

```json
{
  "pointKey": "…(D)", "amount": 1100,
  "remainingCancelableAmount": 100, "balance": 1400,
  "details": [
    { "earnPointKey": "…(A)", "amount": 1000, "reissued": true,  "reissuedPointKey": "…(E)" },
    { "earnPointKey": "…(B)", "amount": 100,  "reissued": false, "reissuedPointKey": null }
  ]
}
```

### 에러 응답

```json
{ "code": "EARN_PARTIALLY_USED", "message": "일부가 사용된 적립은 취소할 수 없습니다. 적립: 1000, 잔액: 300" }
```

| 코드 | HTTP | 상황 |
|---|---|---|
| `INVALID_EARN_AMOUNT` | 400 | 1회 적립 가능 금액 범위 밖 |
| `INVALID_EXPIRE_DAYS` | 400 | 만료일 범위 밖 |
| `MAX_BALANCE_EXCEEDED` | 400 | 개인 최대 보유금액 초과 |
| `INSUFFICIENT_BALANCE` | 400 | 사용 가능 포인트 부족 |
| `EARN_PARTIALLY_USED` | 409 | 일부 사용된 적립의 취소 시도 |
| `EARN_ALREADY_CANCELED` | 409 | 이미 취소된 적립 |
| `EARN_ALREADY_EXPIRED` | 409 | 이미 만료된 적립의 취소 시도 |
| `USE_CANCEL_AMOUNT_EXCEEDED` | 409 | 취소 가능 금액 초과 |
| `TRANSACTION_NOT_FOUND` | 404 | 존재하지 않는 pointKey |

---

## 7. 테스트

```bash
./gradlew test
```

총 **53개 테스트, 전부 통과**합니다.

| 테스트 | 내용 |
|---|---|
| `PointScenarioTest` | **과제 명세 4장 예시(A~E)를 그대로 재현** |
| `PointEarnServiceTest` | 적립 금액·만료일 경계값, 최대 보유 한도, 수기지급 구분, 적립취소 4가지 거절 조건 |
| `PointUseServiceTest` | 수기지급 우선 / 만료임박 순 사용, 부분 취소 반복, 만료분 재적립, `manual` 승계 |
| `PointPolicyServiceTest` | 정책 런타임 변경이 즉시 반영되는지, 정책값 자체의 유효성 |
| `PointConcurrencyTest` | 동시 사용/적립 시 초과 사용·한도 초과가 없는지, 최초 요청 동시 진입 |
| `PointApiTest` | HTTP 계층 (성공 흐름, 검증 실패, 에러 코드) |

시간 의존 로직(만료)은 `Clock` 빈을 주입받고, 테스트에서는 `MutableClock` 으로 대체해 시계를 직접 이동시킵니다. `Thread.sleep` 없이 만료 시나리오를 결정적으로 검증할 수 있습니다.

동시성 테스트 예:

```
잔액 500 상태에서 100포인트 사용을 10건 동시 요청 → 정확히 5건 성공, 5건 INSUFFICIENT_BALANCE, 최종 잔액 0
```

---

## 8. 명세에 없어 가정한 사항

명세에 명시되지 않아 판단이 필요했던 부분과 그 근거입니다.

1. **사용취소로 인한 복원·재적립은 최대 보유 한도를 검증하지 않습니다.**
   한도 때문에 정당한 취소가 실패하면 사용자의 포인트가 사라집니다. 취소는 이미 성립한 사용을 되돌리는 것이므로 신규 적립과 다르게 취급했습니다.

2. **만료된 적립은 적립취소할 수 없습니다** (`EARN_ALREADY_EXPIRED`).
   만료분은 이미 잔액이 없어 차감 대상이 없습니다. 명세에는 "일부 사용 시 불가"만 있으나 만료도 같은 이유로 막았습니다.

3. **사용됐다가 전액 취소되어 잔액이 원금으로 돌아온 적립은 적립취소가 가능합니다.**
   "일부가 사용된 경우"를 *사용 이력이 있는지*가 아니라 *현재 사용 중인 금액이 있는지*로 해석했습니다.

4. **사용자 인증·인가는 구현 범위에서 제외했습니다.** `userId` 를 요청으로 받습니다. 실제 서비스라면 관리자 API는 내부망 + 별도 인증이 필요합니다.

5. **주문 시스템 연동은 동기 API 호출을 가정했습니다.** 결제 흐름상 즉시 응답이 필요하기 때문입니다.

6. **pointKey는 UUID입니다.** 과제 예시의 A/B/C/D/E는 설명용 기호로 해석했습니다.

---

## 9. 프로젝트 구조

```
src/main/java/com/musinsa/payments/point/
├── api/                  컨트롤러, 요청 DTO
├── config/               Clock·OpenAPI 빈, 정책 초기값 프로퍼티/시딩
├── domain/               엔티티. 검증과 상태 전이 규칙이 여기 있음
│   ├── PointTransaction    pointKey 부여 대상
│   ├── PointLot            적립 단위. use/restore/cancelEarn/expire
│   ├── PointLotUsage       사용 상세. cancelableAmount/cancel
│   ├── PointLotUsageCancel 사용취소 상세
│   ├── PointWallet         락 대상
│   └── PointPolicy         정책값과 정책 검증
├── repository/
├── service/              트랜잭션 경계와 흐름 조율
│   ├── PointEarnService        적립 / 적립취소
│   ├── PointUseService         사용 / 사용취소 (재적립 포함)
│   ├── PointQueryService       잔액·이력·주문별 추적
│   ├── PointPolicyService      정책 조회·변경·시딩
│   ├── PointExpirationService  만료 배치 오케스트레이션
│   ├── PointExpirationProcessor  청크 단위 트랜잭션
│   └── PointWalletLocker       사용자 단위 직렬화
└── support/error/        에러 코드, 예외, 전역 핸들러
```

금액 계산 규칙과 상태 전이 조건은 서비스가 아니라 **엔티티 안**에 있습니다. 잘못된 차감·복원은 서비스 어디서 호출하든 엔티티에서 막힙니다.

---

## 10. 한계와 개선 방향

- **멱등성 키가 없습니다.** 적립·사용 API는 네트워크 재시도 시 중복 처리될 수 있습니다. 실서비스라면 `Idempotency-Key` 헤더와 요청 해시 저장이 필요합니다. (적립취소·사용취소는 대상 거래의 상태와 잔여 취소가능 금액으로 중복이 방지됩니다.)
- **잔액 조회가 매번 합산입니다.** 5-1에 적은 대로 적립분이 많아지면 스냅샷 도입이 필요합니다.
- **만료 배치가 단일 인스턴스 가정입니다.** 다중 인스턴스에서는 리더 선출이나 분산 락이 필요합니다.
- **`MERGE ... KEY` 는 H2 문법입니다.** MySQL 이관 시 `INSERT ... ON DUPLICATE KEY UPDATE` 로 바꿔야 합니다.
- **FK 제약을 걸지 않았습니다.** 애플리케이션 레벨 참조로 두어 이후 샤딩·아카이빙 유연성을 확보했습니다. 대신 참조 무결성은 서비스와 테스트로 보장합니다.
