ACCESS_TOKEN=$(curl -s -X POST https://jongju.duckdns.org:8080/api/auth/testuser | jq -r '.data.accessToken')

k6 run --env BASE_URL=http://jongju.duckdns.org:8080 --env API_PREFIX=/api --env ACCESS_TOKEN=$ACCESS_TOKEN scripts/k6/baseline.js

## Outbox Scheduler 부하 테스트

코스 상태 제보 API와 Outbox Scheduler 알림 생성 흐름은 아래 스크립트로 측정한다.

```bash
k6 run \
  --env BASE_URL=http://<host>:8080 \
  --env API_PREFIX=/api \
  --env COURSE_ID=<seeded-course-id> \
  --env RATE=1 \
  --env DURATION=1m \
  scripts/k6/outbox-scheduler-load.js
```

시드 SQL과 검증 SQL은 `scripts/k6/outbox-scheduler-load.md`에 정리한다.
