# 무료 포인트 시스템 (API)

무신사페이먼츠 Backend Engineer 과제. 무료 포인트의 **적립 / 적립취소 / 사용 / 사용취소**를 제공하는 REST API입니다.

---

## 개발 환경

| 항목 | 버전 |
|---|---|
| Java | 21 |
| Spring Boot | 3.5.16 |
| DB | H2 (in-memory) |
| Build | Gradle 8.14.3 (wrapper 포함) |

---

## 빌드 및 실행

JDK 21만 있으면 됩니다. 저장소에 포함된 wrapper로 실행됩니다.

```bash
./gradlew clean build
```

```bash
./gradlew bootRun
```

| | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| H2 콘솔 | http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:point`, User: `sa`, 비밀번호 없음) |

`bootRun` 은 `local` 프로파일로 떠서 SQL 로그와 H2 콘솔이 켜집니다. 패키징한 jar 는 기본 프로파일로 떠서 둘 다 꺼집니다.

---

## 요구사항 구현

| # | 요구사항 | 구현 |
|---|---|---|
| 3-1-1 | 1회 적립 1P 이상 10만P 이하, **하드코딩 아닌 방법으로 제어** | `point_policy` 테이블 + 관리자 API. 초기값은 `application.yml` |
| 3-1-2 | 개인별 최대 보유금액 제한, **별도 방법으로 변경 가능** | 동일. `PUT /api/v1/admin/points/policies` 로 무중단 변경 |
| 3-1-3 | 적립분이 **어떤 주문에서 1원 단위로** 사용됐는지 추적 | `point_usage` (적립분 × 사용거래 × 주문번호 × 금액) |
| 3-1-4 | 관리자 수기지급분을 다른 적립과 **구분 식별** | `earned_point.manual` + 전용 엔드포인트 `POST /api/v1/admin/points/earn` |
| 3-1-5 | 만료일 최소 1일 ~ **5년 미만**, 기본 365일 | 정책값(`min/max/defaultExpireDays`). 5년 미만은 도메인 불변식으로 상한 고정 |
| 3-2-1 | 적립 취소는 전액만, **일부라도 사용됐으면 불가** | `EarnedPoint.cancel()` 에서 `remaining == original` 검증 |
| 3-3-1 | 주문 시에만 사용 | 사용 API가 `orderId` 필수 |
| 3-3-2 | 사용 시 주문번호 기록 | `point_transaction.order_id` + `point_usage.order_id` |
| 3-3-3 | **수기지급 우선 → 만료임박 순** 사용 | `ORDER BY manual DESC, expire_at ASC, id ASC` |
| 3-4-1 | 전체 또는 일부 사용취소 | 부분취소 누적 지원 (`canceled_amount`) |
| 3-4-2 | 취소 시점에 **이미 만료된 적립분은 신규적립 처리** | 복원 불가 시 새 EARN 거래 + 새 적립분 생성 |

명세 4장의 예시(A~E 전체 흐름)는 [`PointScenarioTest`](src/test/java/com/musinsa/payments/point/PointScenarioTest.java) 에 그대로 재현되어 있습니다.

- ERD — [`resource/erd.png`](resource/erd.png) ([SVG](resource/erd.svg))
- AWS 아키텍처 (옵션) — [`resource/aws-architecture.png`](resource/aws-architecture.png) ([SVG](resource/aws-architecture.svg))

---

## 도메인 모델

![ERD](resource/erd.png)

| 테이블 | 역할 |
|---|---|
| `point_transaction` | **pointKey를 부여받는 모든 거래 이벤트**. EARN / EARN_CANCEL / USE / USE_CANCEL |
| `earned_point` | EARN 거래와 1:1. `remaining_amount`, `expire_at`, `manual`, `status` 보유. **잔액의 유일한 원천** |
| `point_usage` | 사용 상세. "어떤 사용거래가 어떤 적립분을 어떤 주문에서 얼마 썼는가" — 1원 단위 추적의 핵심 |
| `point_usage_cancellation` | 사용취소 상세. 어느 적립분으로 돌아갔는지, 만료로 새로 적립했는지 기록 |
| `user_point_lock` | **잔액 컬럼 없음.** 사용자 단위 락(`SELECT ... FOR UPDATE`) 대상으로만 존재 |
| `point_policy` | 단일 행. 런타임에 변경 가능한 정책값 |

잔액은 컬럼으로 저장하지 않고 `SUM(earned_point.remaining_amount)` 로 계산합니다. 계산식에 `expire_at > now()` 가 들어 있어 만료 배치와 무관하게 항상 정확합니다.

DDL과 인덱스는 [`src/main/resources/db/schema.sql`](src/main/resources/db/schema.sql) 에 있습니다.

---

## API

전체 명세는 Swagger UI에서 확인할 수 있습니다. 네 연산 모두 요청 본문에 `requestKey`(선택)를 받으며, 같은 키로 재전송하면 중복 처리 없이 최초 결과를 돌려줍니다.

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/v1/points/earn` | 적립 |
| POST | `/api/v1/points/earn/{pointKey}/cancel` | 적립 취소 |
| POST | `/api/v1/points/use` | 사용 |
| POST | `/api/v1/points/use/{pointKey}/cancel` | 사용 취소 (부분 가능) |
| GET | `/api/v1/points/balance?userId=` | 잔액 및 적립분 목록 |
| GET | `/api/v1/points/transactions?userId=` | 거래 이력 (페이징) |
| GET | `/api/v1/points/orders/{orderId}/usages` | **주문별 1원 단위 사용 추적** |
| POST | `/api/v1/admin/points/earn` | 수기 지급 (`manual = true`) |
| GET · PUT | `/api/v1/admin/points/policies` | 정책 조회 / 변경 |
| POST | `/api/v1/admin/points/expirations` | 만료 배치 수동 실행 |

```bash
curl -X POST http://localhost:8080/api/v1/points/use \
  -H 'Content-Type: application/json' \
  -d '{"userId":1,"orderId":"A1234","amount":1200}'
```

```json
{
  "pointKey": "b553be55-...", "orderId": "A1234", "amount": 1200, "balance": 300,
  "details": [
    { "earnPointKey": "301ce53b-...", "amount": 300, "manual": true },
    { "earnPointKey": "d634ab79-...", "amount": 900, "manual": false }
  ]
}
```

수기지급분이 만료일과 무관하게 먼저 사용된 것을 응답에서 확인할 수 있습니다. 에러는 `{"code": "...", "message": "...", "requestId": "..."}` 형태로 내려갑니다.

---

## 테스트

```bash
./gradlew test
```

총 **104개, 전부 통과**합니다.

- **단위 테스트 40개** — 도메인 규칙(적립 금액·만료일 경계, 차감/복원/취소 전이, 취소 가능 금액). 스프링 없이 0.06초
- **통합 테스트 64개** — 명세 예시 시나리오, 동시 사용/적립, 멱등성, 쿼리 수, HTTP 계층. 컨텍스트 1개 공유

시간 의존 로직(만료)은 `Clock` 을 주입받고 테스트에서 `MutableClock` 으로 대체합니다. `Thread.sleep` 없이 만료 시나리오를 결정적으로 검증합니다.

---

## 명세에 없어 가정한 사항

1. **사용취소로 인한 복원·재적립은 최대 보유 한도를 검증하지 않습니다.** 한도 때문에 정당한 취소가 실패하면 사용자의 포인트가 사라집니다.
2. **만료된 적립은 적립취소할 수 없습니다** (`EARN_ALREADY_EXPIRED`). 만료분은 이미 잔액이 없어 차감 대상이 없습니다.
3. **사용됐다가 전액 취소되어 잔액이 원금으로 돌아온 적립은 적립취소가 가능합니다.** "일부가 사용된 경우"를 *현재 사용 중인 금액이 있는지*로 해석했습니다.
4. **사용자 인증·인가는 구현 범위에서 제외했습니다.** `userId` 를 요청으로 받습니다.
5. **주문 시스템 연동은 동기 API 호출을 가정했습니다.** 결제 흐름상 즉시 응답이 필요하기 때문입니다.
6. **pointKey는 UUID입니다.** 과제 예시의 A/B/C/D/E는 설명용 기호로 해석했습니다.
