# Outbox Scheduler load test

## 목적

코스 상태 제보 등록 API와 Outbox Scheduler 알림 생성 흐름의 운영 지표를 분리해 측정한다.

이 테스트는 정합성 테스트가 아니라 서버 환경에서 다음 값을 관측하기 위한 부하 테스트다.

- 제보 등록 API 응답 시간 p50/p95/p99
- `event_outbox.created_at`부터 `processed_at`까지의 처리 지연 p95/p99
- 테스트 종료 후 Outbox backlog
- 동일 `event_id`, `user_id` 기준 중복 알림 생성 여부
- HikariCP active/pending connection 상태

## Seed data

부하 테스트 전에 RDS에 코스와 구독자를 준비한다. 가입/구독 API를 부하 테스트에 섞으면 Outbox 처리 특성과 데이터 준비 비용이 섞이므로 DB에 직접 seed 한다.

```sql
SET @subscriber_count := 100;

INSERT INTO courses (name, current_status, created_at)
SELECT 'outbox-scheduler-load-course', 'NORMAL', NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM courses WHERE name = 'outbox-scheduler-load-course'
);

SET @course_id := (
    SELECT id
    FROM courses
    WHERE name = 'outbox-scheduler-load-course'
    ORDER BY id
    LIMIT 1
);

DROP TEMPORARY TABLE IF EXISTS load_seq;
CREATE TEMPORARY TABLE load_seq (n INT PRIMARY KEY);

INSERT INTO load_seq
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < @subscriber_count
)
SELECT n FROM seq;

INSERT INTO users (id, email, password, nickname, deleted, created_at, role)
SELECT
    UUID_TO_BIN(UUID()),
    CONCAT('outbox-sub-', LPAD(n, 10, '0'), '@load.test'),
    'load-test-password',
    CONCAT('ol', LPAD(n, 10, '0')),
    0,
    NOW(6),
    'USER'
FROM load_seq
ON DUPLICATE KEY UPDATE email = email;

INSERT IGNORE INTO course_subscriptions (course_id, user_id, created_at)
SELECT @course_id, id, NOW(6)
FROM users
WHERE email LIKE 'outbox-sub-%@load.test';

SELECT
    @course_id AS course_id,
    @subscriber_count AS target_subscribers,
    (
        SELECT COUNT(*)
        FROM course_subscriptions
        WHERE course_id = @course_id
    ) AS subscriptions;
```

구독자 수를 1000명보다 크게 잡을 때는 MySQL recursive CTE 제한을 먼저 늘린다.

```sql
SET SESSION cte_max_recursion_depth = 100000;
```

## Run

EC2 서버에서 애플리케이션이 떠 있는 상태로 실행한다. `BASE_URL`은 애플리케이션이 실제로 열려 있는 주소를 사용한다.

```bash
mkdir -p results

k6 run \
  --env BASE_URL=http://52.79.243.92:8080 \
  --env API_PREFIX=/api \
  --env COURSE_ID=<seeded-course-id> \
  --env RATE=1 \
  --env DURATION=1m \
  scripts/k6/outbox-scheduler-load.js
```

Docker로 k6를 실행할 때는 호스트 네트워크를 사용한다.

```bash
mkdir -p results

docker run --rm --network host \
  --user "$(id -u):$(id -g)" \
  -v "$PWD/scripts/k6:/scripts/k6" \
  -v "$PWD/results:/home/k6/results" \
  grafana/k6 run \
  --env BASE_URL=http://127.0.0.1:8080 \
  --env API_PREFIX=/api \
  --env COURSE_ID=<seeded-course-id> \
  --env RATE=1 \
  --env DURATION=1m \
  /scripts/k6/outbox-scheduler-load.js
```

첫 실행은 낮은 부하로 시작한다.

```text
RATE=1, DURATION=1m
RATE=5, DURATION=2m
```

## Verify

테스트 직후와 scheduler가 따라잡을 시간을 준 뒤 각각 확인한다.

```sql
SELECT status, COUNT(*) AS count
FROM event_outbox
GROUP BY status;

SELECT COUNT(*) AS duplicate_notifications
FROM (
    SELECT event_id, user_id, COUNT(*) AS count
    FROM notifications
    GROUP BY event_id, user_id
    HAVING COUNT(*) > 1
) duplicated;

WITH delay AS (
    SELECT
        TIMESTAMPDIFF(MICROSECOND, created_at, processed_at) / 1000 AS delay_ms,
        CUME_DIST() OVER (
            ORDER BY TIMESTAMPDIFF(MICROSECOND, created_at, processed_at)
        ) AS cume
    FROM event_outbox
    WHERE status = 'PROCESSED'
      AND processed_at IS NOT NULL
)
SELECT MIN(delay_ms) AS outbox_delay_p95_ms
FROM delay
WHERE cume >= 0.95;

WITH delay AS (
    SELECT
        TIMESTAMPDIFF(MICROSECOND, created_at, processed_at) / 1000 AS delay_ms,
        CUME_DIST() OVER (
            ORDER BY TIMESTAMPDIFF(MICROSECOND, created_at, processed_at)
        ) AS cume
    FROM event_outbox
    WHERE status = 'PROCESSED'
      AND processed_at IS NOT NULL
)
SELECT MIN(delay_ms) AS outbox_delay_p99_ms
FROM delay
WHERE cume >= 0.99;

SELECT COUNT(*) AS reports
FROM course_reports
WHERE course_id = <seeded-course-id>;

SELECT COUNT(*) AS notifications
FROM notifications n
JOIN course_reports cr ON cr.id = n.course_report_id
WHERE cr.course_id = <seeded-course-id>;
```

Actuator가 열려 있으면 DB connection 지표도 같이 확인한다.

```bash
curl -s http://52.79.243.92:8080/api/actuator/metrics/hikaricp.connections.active
curl -s http://52.79.243.92:8080/api/actuator/metrics/hikaricp.connections.pending
```

## 기록 형식

결과는 실제 측정값만 남긴다.

```text
subscribers | rate | duration | api_p95_ms | outbox_delay_p95_ms | pending | failed | duplicate_notifications | hikaricp_active_max | hikaricp_pending_max
```
