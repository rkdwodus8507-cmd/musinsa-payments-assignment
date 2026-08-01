# 설계 결정과 한계

무료 포인트 시스템에서 판단이 필요했던 지점과 그 근거입니다. 과제 제출물의 본문은 [README](../README.md) 이고, 이 문서는 그 배경입니다.

---

## 계층 구조

`controller → service → repository` 입니다. facade 는 두지 않았습니다 — 컨트롤러 메서드 11개 전부가 서비스 메서드를 정확히 하나만 호출하므로 위임만 하는 층이 늘어날 뿐입니다. 한 요청이 여러 서비스를 조율해야 할 때 넣는 게 맞다고 봤습니다.

지키는 규칙은 셋입니다.

- **엔티티는 서비스 계층을 벗어나지 않습니다.** 컨트롤러 반환 타입은 전부 DTO 이고 `service/dto` 의 어떤 클래스도 엔티티를 import 하지 않습니다.
- **서비스가 다른 서비스를 호출하지 않습니다.** 정책·거래·적립분 조회는 `PointPolicyReader` / `PointTransactionReader` / `EarnedPointReader` 가 맡습니다.
- **컨트롤러는 리포지토리도 설정도 보지 않습니다.**

의존 방향은 전부 안쪽(도메인)을 향합니다. `api → domain` 이 한 군데 있는데(`UpdatePolicyRequest.toValues()`) 엔티티도 응답도 아닌 도메인 값 객체이고 방향이 안쪽이라 그대로 뒀습니다.

---

---

## 핵심 설계 결정

### 잔액을 컬럼으로 저장하지 않는다

`user_point_lock.balance` 같은 캐시 컬럼 없이, 잔액은 항상 이렇게 계산합니다.

```sql
SELECT COALESCE(SUM(remaining_amount), 0) FROM earned_point
WHERE user_id = ? AND status = 'AVAILABLE' AND expire_at > now()
```

만료는 시각이 지나면 발생하는데 컬럼 차감은 배치가 돌아야 반영됩니다. 잔액 컬럼을 두면 그 사이에 이미 만료된 포인트가 사용 가능한 것처럼 보입니다. 계산식에 `expire_at > now()` 가 들어 있으면 배치와 무관하게 항상 정확합니다.

적립분이 수만 건까지 쌓이면 합산 비용이 문제가 됩니다. 그때는 스냅샷 테이블을 두되 원장을 정본으로 유지하는 방향이 맞다고 봅니다.

### 잔액을 바꾸는 모든 경로가 같은 락을 잡는다

검사와 반영 사이가 원자적이어야 하므로 네 연산 모두 진입부에서 사용자 행을 잠급니다. 사용자 단위라 다른 사용자끼리는 경합하지 않습니다.

```java
userPointLocker.lock(userId);   // SELECT ... FOR UPDATE
```

**만료 배치도 같은 락을 잡습니다.** 잡지 않으면 좁지만 실제로 돈이 새는 경합이 있습니다.

```
1. 사용 트랜잭션: 락 획득 → expire_at > now 로 적립분 A 를 사용 가능으로 읽음
2. 만료 배치(락 없음): 같은 A 를 expire_at <= now 로 읽어 EXPIRED 로 전이 → 커밋
3. 사용 트랜잭션: A 에서 차감 → remaining_amount 만 update → 커밋
   결과: EXPIRED 인데 잔액이 줄어든 적립분 = 만료된 포인트가 사용됨
```

그래서 배치를 사용자 단위로 돌립니다. 한 사용자를 처리하면 그 사용자의 만료 대상이 사라지므로 재조회가 비면 끝납니다. 사용자당 독립 트랜잭션이라 중간에 실패해도 이어서 재개되고 락 점유도 짧습니다.

> 락 행이 없는 최초 요청은 `MERGE INTO user_point_lock ... KEY(user_id)` 로 만듭니다. 처음엔 `REQUIRES_NEW` 중첩 트랜잭션을 썼는데, 요청 하나가 커넥션 2개를 잡아 동시 요청이 풀 크기를 넘으면 교착에 빠졌습니다. 동시성 테스트에서 드러나 upsert 로 교체했습니다. MySQL 로 옮기면 이 한 줄만 `INSERT ... ON DUPLICATE KEY UPDATE` 로 바꾸면 됩니다.

### 정책값은 DB에 두고 API로 바꾼다

yml `@ConfigurationProperties` 는 값을 바꾸려면 재기동해야 해서 "별도의 방법으로 변경 가능"이라는 요구를 온전히 만족하지 못한다고 봤습니다. `point_policy` 테이블 + 관리자 API 로 두고, yml 값은 최초 기동 시 시딩용으로만 씁니다.

```bash
curl -X PUT http://localhost:8080/api/v1/admin/points/policies \
  -H 'Content-Type: application/json' \
  -d '{"minEarnAmount":1,"maxEarnAmount":200000,"maxUserBalance":1000000,
       "defaultExpireDays":365,"minExpireDays":1,"maxExpireDays":1824}'
```

`maxExpireDays` 상한 **1824일**은 "5년 미만"을 일 단위로 옮긴 값입니다. 이건 시스템 불변식이라 정책값으로 열지 않고 도메인에서 고정 검증합니다.

### 사용 순서와 사용취소 순서

**사용**: `manual DESC, expire_at ASC, id ASC` — 수기지급분 우선, 그다음 만료 임박 순.

**사용취소**: 사용된 순서 그대로(`point_usage.id ASC`) 되돌립니다. 과제 예시의 "1200원 중 1100원 취소 → A 1000 전액 + B 100" 과 일치합니다.

**만료분 복원**: 복원 대상이 만료됐으면 되살릴 수 없으므로 새 EARN 거래와 적립분을 만듭니다(예시의 E). 만료일은 정책 기본값으로 새로 부여하고 `manual` 은 원래 적립분에서 승계합니다 — 수기지급분이 일반 적립분으로 바뀌면 사용 우선순위가 밀려 사용자에게 불리하기 때문입니다.

### 만료 기준 시간대를 코드가 정한다

잔액 계산은 `expire_at > now()` 로 실시간 판정하고, 배치(기본 매일 04:00)는 지난 적립분을 `EXPIRED` 로 전이시켜 이력을 남기고 조회 대상을 줄이는 역할만 합니다.

`expire_at` 은 `LocalDateTime` 이라 기준 시간대를 정하지 않으면 **서버 기본 시간대에 끌려갑니다.** 컨테이너가 UTC 로 뜨면 한국 사용자의 만료가 9시간 밀립니다. 돈이 걸린 경계값이 배포 환경에 좌우되면 안 되므로 설정으로 못박고 `Clock` 을 그 시간대로 고정했습니다.

```yaml
point:
  time-zone: Asia/Seoul
```

`Instant` 로 저장하는 쪽이 이론적으로는 더 정직하지만, 이 시스템의 만료는 "한국 시간 기준 며칠"이라는 업무 규칙이라 기준 시간대가 하나뿐입니다. 엔티티·DTO·테스트를 전부 바꾸는 비용에 비해 얻는 게 없다고 봤습니다.

### 멱등성은 락 안에서 판정한다

네 연산 모두 `requestKey` 를 받습니다. 같은 키로 재전송되면 새 거래를 만들지 않고 **처음 처리한 거래의 결과를 그대로 다시 조립해서** 돌려줍니다.

```java
public UseResult use(UseCommand command) {
    userPointLocker.lock(command.getUserId());

    return idempotencyGuard.runOnce(command.getUserId(), command.getRequestKey(), USE,
            this::toUseResult,            // 이미 처리했으면 그 거래로 결과 재현
            () -> deductPoints(command)); // 처음이면 실제 차감
}
```

순서가 중요합니다. **락을 먼저 잡고 그다음에 requestKey 를 조회합니다.** 반대면 동시에 도착한 두 요청이 모두 "처리 이력 없음"으로 판정해 중복 차감이 일어납니다. `(user_id, request_key)` 유니크 제약이 마지막 안전망입니다.

`toUseResult(transaction)` 은 최초 처리 경로와 재전송 경로가 함께 쓰는 단 하나의 응답 조립 지점입니다. 두 경로가 각자 응답을 만들면 시간이 지나며 어긋납니다.

- `requestKey` 를 안 보내면 중복 차단은 적용되지 않습니다.
- 같은 키를 다른 종류의 요청에 재사용하면 `REQUEST_KEY_CONFLICT` 로 거절합니다.
- 같은 키에 **다른 금액**이 오면 최초 결과를 반환합니다. 엄격히는 422 로 거절해야 맞지만 단순함을 택했습니다.

### N+1 은 짐작하지 않고 센다

정규화를 유지하면 상세 응답을 만들 때 `point_usage → earned_point → point_transaction` 을 타야 합니다. 건별로 타면 N+1 이 되고, 값을 복사해 두면 정규화가 깨집니다. **id 를 모아 한 번에 읽습니다.**

```java
EarnedPointSources sources = earnedPointReader.sourcesOf(usages);
```

Hibernate 통계로 재보니 적립분당 늘어나는 문장은 전부 없앨 수 없는 쓰기뿐이고 조회는 늘지 않습니다.

| | 적립분당 문장 |
|---|---|
| 사용 | **2** (사용상세 insert + 적립분 update) |
| 사용취소 | **3** (사용상세 update + 적립분 update + 취소상세 insert) |
| 만료분 재적립 취소 | **4** (+ 거래 insert + 적립분 insert) |

`reissue` 안의 `policyReader.current()` 는 루프 안에 있지만 N+1 이 아닙니다. 영속성 컨텍스트 1차 캐시가 두 번째 호출부터 DB 를 타지 않아 문장 수가 고정입니다. 눈으로는 N+1 처럼 보이지만 실제로는 아니어서 그대로 뒀습니다. 이 수치는 `PointQueryCountTest` 가 지킵니다.

**크기에도 상한을 뒀습니다.** 잔액 조회는 적립분을 전부 메모리로 읽지 않고 집계 쿼리로 구하며 목록은 최근 100건까지, 이력 조회 `size` 는 최대 100, `IN` 절 id 는 `IdChunks` 가 1000개씩 나눕니다.

### 사고가 났을 때 추적할 수 있어야 한다

포인트는 "누가 언제 얼마를 왜" 를 사후에 재구성할 수 있어야 합니다. 잔액이 바뀌는 네 지점에서만 전용 로거로 남깁니다.

```
2026-07-30 11:18:53.417  INFO [trace-abc] point-audit : type=EARN pointKey=ccd43d62-... userId=9
  amount=1000 orderId=null requestKey=k9 relatedTransactionId=null balanceAfter=1000
2026-07-30 11:18:53.441  INFO [trace-abc] point-audit : type=EARN pointKey=ccd43d62-... userId=9
  requestKey=k9 duplicate=true
```

- 로거 이름이 `point-audit` 이라 운영에서 별도 파일·수집기로 분리할 수 있습니다.
- `[trace-abc]` 는 `X-Request-Id` 입니다. `RequestIdFilter` 가 헤더를 받거나 없으면 발급해 MDC 에 넣고 응답 헤더와 에러 응답에 실어 보냅니다. 고객이 캡처를 보내오면 그 값으로 서버 로그를 찾을 수 있습니다.
- 재전송은 `duplicate=true` 로 따로 남고 잔액 변경 기록은 남지 않습니다. 같은 `pointKey` 가 두 줄에 걸쳐 나오므로 "중복 요청이 있었지만 한 번만 반영됐다"가 로그만으로 확인됩니다.

### 읽기 위한 규칙

**공개 메서드는 모두 세 줄로 읽힙니다** — 락 → 멱등성 판정 → 실제 처리. 조율하는 private 메서드도 같은 수준의 단계만 담고, 인라인 stream 합산이나 `throw` 는 이름 붙은 단계로 내렸습니다(`validateEnoughToUse`, `deductInPriorityOrder`, `giveBack`, `restoreInUsedOrder`). 메서드 이름만 훑어도 "적립분을 우선순위 순으로 차감한다", "사용된 순서대로 되돌려준다"가 읽힙니다.

**객체를 만들 때는 위치 인자 대신 관련 엔티티를 넘깁니다.** `of(userId, EARN, amount, null, null, requestKey, memo, now)` 처럼 null 이 늘어서면 몇 번째가 무엇인지 알 수 없습니다.

```java
EarnedPoint.from(earnTransaction, manual, expireAt, now)
PointUsage.of(useTransaction, source, amount, now)
```

**`record` 는 쓰지 않았습니다.** 실행 환경의 JDK 를 확정할 수 없을 때 언어 기능보다 평범한 클래스가 안전하다고 봤습니다. 이미 엔티티 전체가 Lombok 을 쓰므로 DTO 도 같은 방식으로 맞췄습니다 — 결과·커맨드는 `@Value`(불변 + `equals`), 요청·프로퍼티는 `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`(파라미터 이름 정보 없이도 되는 JavaBean 역직렬화). JSON 필드명은 record 때와 동일합니다.

**주석은 두지 않았습니다.** 이름으로 설명되지 않는 코드가 있으면 이름을 고쳤습니다. 중복은 스캐너로 훑어 3줄 이상 반복 블록을 22건 → 5건으로 줄였고, 남은 5건은 서로 다른 경계의 매핑·서로 다른 JPQL·서로 다른 DTO 인자 나열이라 합치면 손해입니다.

**개발 편의 설정은 프로파일로 갈랐습니다.** `show-sql`, `DEBUG` 로그, H2 콘솔은 리뷰할 때는 편하지만 그대로 운영에 올리면 안 됩니다. `./gradlew bootRun` 은 `local` 을 자동으로 켜고, 패키징한 jar 는 기본값(조용한 쪽)으로 뜹니다.

---

## 한계와 개선 방향

- **H2 전용 문법이 한 군데 있습니다.** 사용자 락 행 생성이 `merge into user_point_lock ... key (user_id)` 입니다. MySQL 로 옮기면 `insert ... on duplicate key update user_id = user_id` 로 바꾸면 되고, 바꿀 곳은 `UserPointLockRepository.upsert` 한 곳뿐입니다. dialect 별 구현을 미리 만들어두는 건 과제 환경(H2)에서 검증할 수 없는 코드를 남기는 일이라 하지 않았습니다.
- **더 중요한 건 락 동작 차이입니다.** H2 의 `SELECT ... FOR UPDATE` 와 MySQL 의 갭 락 동작이 달라 **지금 통과하는 동시성 테스트가 MySQL 에서 같은 결과를 보장하지 않습니다.** 이관 시 MySQL Testcontainer 로 같은 테스트를 다시 돌려야 합니다.
- **스키마 마이그레이션 도구를 넣지 않았습니다.** 인메모리 H2 로 매번 새로 만드는 과제라 `schema.sql` 한 장이면 충분하고, Flyway 를 넣으면 리뷰어가 스키마를 보려고 마이그레이션 폴더를 뒤져야 합니다. 실서비스라면 Flyway 로 관리하고, 컬럼 추가는 nullable 로 추가 → 배포로 채우기 → `not null` 로 조이기 세 단계로 나눠야 합니다. 배포 중 구버전이 그 컬럼을 모르는 순간이 있기 때문입니다.
- **관리자 API 에 인증이 없습니다.** API 키 한 겹은 실제 보안이 아니면서 리뷰어가 헤더를 알아야 admin API 를 호출할 수 있게 만들 뿐이라 넣지 않았습니다. 실서비스라면 관리자 API 는 내부망에 두고 게이트웨이/mTLS/IAM 으로 막은 뒤, 조작자가 누구인지까지 감사 로그에 남겨야 합니다. 사용자 API 의 `userId` 도 지금은 요청 본문으로 받는데 토큰에서 꺼내야 맞습니다.
- **만료 배치가 단일 인스턴스 가정입니다.** 여러 인스턴스에서 동시에 돌면 같은 적립분을 두고 락 경합만 늘고, 배포 중에는 구·신버전이 겹쳐 돕니다. 실서비스라면 DB 행 선점이나 리더 선출로 하나만 돌게 해야 합니다. 과제 범위에서는 인스턴스가 하나뿐이라 넣지 않았습니다.
- **데이터 수명 정책이 없습니다.** `point_transaction` / `point_usage` 는 무한히 쌓입니다. 오래된 이력은 S3 + Athena 로 아카이빙하고 원장만 남기는 편이 맞습니다.
- **잔액 조회가 매번 합산입니다.** 적립분이 수만 건까지 쌓이면 스냅샷 테이블이 필요합니다 ([잔액을 컬럼으로 저장하지 않는다](#잔액을-컬럼으로-저장하지-않는다)).
- **멱등성 키에 요청 본문 해시를 함께 저장하지 않습니다.** 같은 `requestKey` 에 다른 금액이 오면 최초 결과를 반환합니다. 엄격히는 422 로 거절해야 맞습니다.
- **지표를 내보내지 않습니다.** 감사 로그로 사후 추적은 되지만, 락 대기 시간·배치 지연·만료 적체 같은 값을 지표로 노출하지 않습니다. 실서비스라면 Micrometer 로 노출하고 그중 **만료 적체(만료 시각이 지났는데 미처리인 적립분 수)** 에 경보를 걸어야 합니다 — 배치가 밀리면 사용자가 이미 만료된 포인트를 계속 쓰게 되기 때문입니다.
