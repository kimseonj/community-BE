# Outbox 대량 알림 메모리 개선 측정

## 결론

구독자 100,000명 단일 Outbox 이벤트를 3회 실행한 결과 모두 `PROCESSED`, retry 0회, Notification 100,000건, 중복 0건으로 완료됐다. 추가로 5,000명, 10,000명, 50,000명 코스를 각 1회 실행해 구독자 규모별 처리 시간과 메모리 상한을 확인했다.

개선 후 중앙값은 Outbox 처리 6.92초, Peak Heap 315.1MiB, Peak RSS 697.2MiB, GC 5회였다. 개선 전 기준선보다 처리 시간은 68.7%, Peak Heap은 51.9%, Peak RSS는 36.6% 감소했다.

## 측정 환경

| 항목 | 조건 |
|---|---|
| 기준 커밋 | `c124b1b` |
| Java / Spring Boot | Java 21 / Spring Boot 3.5.6 |
| JVM Heap | `-Xms512m -Xmx1g` |
| Spring 컨테이너 제한 | 2GiB |
| MySQL | 8.4.8 |
| 구독자 | 코스 ID 9: 5,000명, 3: 10,000명, 5: 50,000명, 2: 100,000명 |
| chunk size / batch size | 500 / 500 |
| 모니터링 | Prometheus 1초 수집, Grafana |
| 실행 시각 | 2026-07-05 16:09:37~17:27:55 KST |

기존 DB volume을 유지한 상태에서 애플리케이션 이미지만 현재 커밋으로 다시 빌드했다. 100명 코스로 API와 Scheduler를 warm-up한 뒤, 실행 사이 Heap이 200MiB 아래로 회복된 것을 확인하고 100,000명 이벤트를 호출했다.

## 100,000명 3회 측정

| Run | Outbox ID | API | Outbox 처리 | Heap 시작 | Peak Heap | Heap 증가 | RSS 시작 | Peak RSS | RSS 증가 | GC | GC pause | Hikari active/pending |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 11 | 30.401ms | 6.9212초 | 120.2MiB | 302.3MiB | 182.1MiB | 686.2MiB | 692.7MiB | 6.5MiB | 5회 | 58ms | 1 / 0 |
| 2 | 12 | 39.742ms | 8.1128초 | 180.3MiB | 336.3MiB | 156.0MiB | 694.9MiB | 697.2MiB | 2.3MiB | 5회 | 73ms | 1 / 0 |
| 3 | 13 | 29.643ms | 4.6644초 | 132.7MiB | 315.1MiB | 182.4MiB | 697.9MiB | 712.4MiB | 14.5MiB | 5회 | 91ms | 1 / 0 |
| 중앙값 | - | 30.401ms | 6.9212초 | 132.7MiB | 315.1MiB | 182.1MiB | 694.9MiB | 697.2MiB | 6.5MiB | 5회 | 73ms | 1 / 0 |

각 실행 결과:

- Run 1: 2026-07-05 16:09:37.778~16:09:44.700 KST
- Run 2: 2026-07-05 16:11:11.829~16:11:19.942 KST
- Run 3: 2026-07-05 16:12:05.430~16:12:10.095 KST
- 모든 Run: HTTP 201, Outbox `PROCESSED`, retry 0회, Notification 100,000건, 사용자 중복 0건
- 컨테이너 OOM 또는 재시작 없음

Prometheus 1초 수집 경계에서 처리 완료 직전 sample을 빠뜨리지 않도록 이벤트 생성 시각부터 `processed_at` 이후 첫 sample까지를 처리 구간으로 계산했다. 짧은 순간 peak는 1초 수집으로 놓칠 수 있으므로 수치는 관측된 최댓값이다.

## 규모별 단회 측정

앱을 재시작한 뒤 100명 코스로 warm-up 1회를 실행하고, 5,000명, 10,000명, 50,000명 코스를 각 1회 호출했다. 5,000명과 10,000명은 처리 시간이 1초대라 Prometheus 1초 수집에서 순간 peak나 Hikari active 값을 놓칠 수 있다. 따라서 아래 값은 추세 확인용 관측치로 사용한다.

| 구독자 | Course | Outbox ID | API | Outbox 처리 | Heap 시작 | Peak Heap | Heap 증가 | RSS 시작 | Peak RSS | RSS 증가 | GC | GC pause | Hikari active/pending | Notification / 중복 |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 5,000 | 9 | 15 | 43.554ms | 1.0460초 | 183.7MiB | 276.7MiB | 93.0MiB | 674.9MiB | 677.9MiB | 3.1MiB | 0회 | 0ms | 0 / 0 | 5,000 / 0 |
| 10,000 | 3 | 16 | 49.079ms | 1.2127초 | 311.7MiB | 317.7MiB | 6.0MiB | 678.6MiB | 681.4MiB | 2.7MiB | 1회 | 42ms | 1 / 0 | 10,000 / 0 |
| 50,000 | 5 | 17 | 59.293ms | 4.8497초 | 237.0MiB | 312.3MiB | 75.4MiB | 681.8MiB | 688.2MiB | 6.4MiB | 3회 | 43ms | 1 / 0 | 50,000 / 0 |

각 실행 결과:

- Warm-up 100명: 2026-07-05 17:27:00.082~17:27:00.441 KST, Outbox ID 14
- 5,000명: 2026-07-05 17:27:16.489~17:27:17.535 KST
- 10,000명: 2026-07-05 17:27:33.621~17:27:34.834 KST
- 50,000명: 2026-07-05 17:27:51.056~17:27:55.906 KST
- 모든 측정: HTTP 201, Outbox `PROCESSED`, retry 0회, 사용자 중복 0건

## 개선 전·후 비교

| 지표 | 개선 전 | 개선 후 중앙값 | 변화 |
|---|---:|---:|---:|
| API 응답 | 24.66ms | 30.401ms | 같은 수십 ms 범위 |
| Outbox 처리 시간 | 22.10초 | 6.92초 | 68.7% 감소, 3.19배 처리 |
| Peak Heap | 654.9MiB | 315.1MiB | 51.9% 감소 |
| Heap 증가분 | 482.0MiB | 182.1MiB | 62.2% 감소 |
| Peak RSS | 1,099.8MiB | 697.2MiB | 36.6% 감소 |
| RSS 증가분 | 241.9MiB | 6.5MiB | 97.3% 감소 |
| 처리 구간 GC | 24회 | 5회 | 79.2% 감소 |
| GC pause | 약 372ms | 73ms | 80.4% 감소 |
| Hikari pending | 0 | 0 | 동일 |
| Notification / 중복 | 100,000 / 0 | 100,000 / 0 | 정합성 유지 |

RSS 증가분은 실행 전 JVM 상태와 OS 메모리 회수 시점의 영향을 크게 받으므로 성과 문구에는 Peak RSS를 우선 사용한다. API 응답 차이도 단일 요청의 수십 ms 변동이므로 개선율로 주장하지 않는다.

## 결과 해석

개선 전에는 전체 구독자와 Notification 엔티티를 한 트랜잭션에서 관리하고 JDBC 자원도 처리 종료까지 보유했다. 개선 후에는 구독자의 `subscription_id`, `user_id`만 500건씩 조회하고 각 chunk를 JDBC batch insert 후 독립 commit한다.

그 결과 한 번에 생존하는 조회 결과와 batch 인자가 chunk 크기로 제한됐다. 규모별 단회 측정에서도 5,000명에서 50,000명으로 처리 건수가 10배 늘 때 처리 시간은 1.05초에서 4.85초로 증가했지만, Peak Heap은 276.7~317.7MiB 범위에 머물렀다.

100,000명 처리 정합성을 유지하면서 Peak Heap, Peak RSS, GC 횟수와 처리 시간이 함께 감소했으므로 메모리 상한과 저장 처리량이 모두 개선된 것으로 판단한다.

## 기존 DB schema 사전조건

기존 volume의 `event_outbox.status`는 `ENUM('FAILED','PENDING','PROCESSED')`여서 새 `PROCESSING` 값을 저장할 수 없었다. Hibernate `ddl-auto=update`는 `processing_started_at` 컬럼은 추가했지만 기존 ENUM 값은 확장하지 않았다.

측정 전 다음 변경을 적용했다.

```sql
ALTER TABLE event_outbox
MODIFY COLUMN status
ENUM('FAILED','PENDING','PROCESSED','PROCESSING') NOT NULL;
```

신규 schema에는 현재 enum 전체가 생성되지만 기존 DB를 업그레이드할 때는 위 변경이 필요하다. 이 migration 없이 Scheduler가 `PROCESSING` claim을 commit하지 못하므로 배포 전에 별도 반영해야 한다.
