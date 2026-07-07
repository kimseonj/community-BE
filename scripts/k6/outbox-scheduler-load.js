import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_PREFIX = __ENV.API_PREFIX || '/api';
const COURSE_ID = __ENV.COURSE_ID;
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN || '';
const RATE = Number(__ENV.RATE || 1);
const DURATION = __ENV.DURATION || '1m';
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || 10);
const MAX_VUS = Number(__ENV.MAX_VUS || 50);
const SLEEP_SECONDS = Number(__ENV.SLEEP_SECONDS || 0);

const reportCreateLatency = new Trend('report_create_latency', true);
const reportCreateErrorRate = new Rate('report_create_error_rate');
const reportCreateServerErrors = new Counter('report_create_server_errors');

const REPORT_TYPES = ['CAUTION', 'CONSTRUCTION', 'CLOSED', 'NORMAL'];

export const options = {
    summaryTrendStats: ['min', 'avg', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
    scenarios: {
        report_create: {
            executor: 'constant-arrival-rate',
            rate: RATE,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: PRE_ALLOCATED_VUS,
            maxVUs: MAX_VUS,
        },
    },
    thresholds: {
        checks: ['rate>0.99'],
        report_create_error_rate: ['rate<0.01'],
    },
};

export function setup() {
    if (!COURSE_ID) {
        throw new Error('COURSE_ID is required. Seed a course first and pass --env COURSE_ID=<id>.');
    }

    const accessToken = ACCESS_TOKEN || issueTestUserAccessToken();
    const health = http.get(`${BASE_URL}${API_PREFIX}/actuator/health`, {
        tags: { name: 'GET_health' },
    });

    check(health, {
        'health status is 200': (res) => res.status === 200,
        'health is UP': (res) => res.status === 200 && jsonValue(res, 'status') === 'UP',
    });

    return {
        accessToken,
        courseId: COURSE_ID,
    };
}

export default function (data) {
    group('POST /courses/{courseId}/reports', () => {
        const type = REPORT_TYPES[(__ITER + __VU) % REPORT_TYPES.length];
        const payload = JSON.stringify({
            type,
            content: `outbox scheduler load test type=${type} vu=${__VU} iter=${__ITER} ts=${Date.now()}`,
        });

        const res = http.post(`${BASE_URL}${API_PREFIX}/courses/${data.courseId}/reports`, payload, {
            headers: {
                'Content-Type': 'application/json',
                Cookie: `accessToken=${data.accessToken}`,
            },
            tags: { name: 'POST_course_report' },
        });

        const success = check(res, {
            'report status is 201': (r) => r.status === 201,
        });

        reportCreateLatency.add(res.timings.duration);
        reportCreateErrorRate.add(!success);

        if (res.status >= 500) {
            reportCreateServerErrors.add(1);
        }
    });

    if (SLEEP_SECONDS > 0) {
        sleep(SLEEP_SECONDS);
    }
}

export function handleSummary(data) {
    const latency = data.metrics.report_create_latency?.values || {};
    const errors = data.metrics.report_create_error_rate?.values || {};
    const serverErrors = data.metrics.report_create_server_errors?.values || {};
    const requests = data.metrics.http_reqs?.values || {};

    const summary = {
        timestamp: new Date().toISOString(),
        config: {
            baseUrl: BASE_URL,
            apiPrefix: API_PREFIX,
            courseId: COURSE_ID,
            rate: `${RATE}/s`,
            duration: DURATION,
            preAllocatedVUs: PRE_ALLOCATED_VUS,
            maxVUs: MAX_VUS,
        },
        reportCreate: {
            avg: formatMs(latency.avg),
            p50: formatMs(latency['p(50)'] || latency.med),
            p90: formatMs(latency['p(90)']),
            p95: formatMs(latency['p(95)']),
            p99: formatMs(latency['p(99)']),
            max: formatMs(latency.max),
            errorRate: formatPercent(errors.rate),
            serverErrors: serverErrors.count || 0,
        },
        requests: {
            total: requests.count || 0,
            rps: requests.rate ? Number(requests.rate.toFixed(2)) : 0,
        },
    };

    return {
        stdout: textSummary(summary),
        'results/outbox_scheduler_load_result.json': JSON.stringify(summary, null, 2),
    };
}

function issueTestUserAccessToken() {
    const res = http.post(`${BASE_URL}${API_PREFIX}/auth/testuser`, null, {
        headers: { 'Content-Type': 'application/json' },
        tags: { name: 'POST_auth_testuser' },
    });

    const success = check(res, {
        'test token issued': (r) => r.status === 200 && Boolean(jsonValue(r, 'data.accessToken')),
    });

    if (!success) {
        throw new Error(`Failed to issue test access token. status=${res.status} body=${res.body}`);
    }

    return jsonValue(res, 'data.accessToken');
}

function jsonValue(res, path) {
    try {
        return res.json(path);
    } catch (e) {
        return null;
    }
}

function formatMs(value) {
    return Number.isFinite(value) ? `${value.toFixed(2)}ms` : null;
}

function formatPercent(value) {
    return Number.isFinite(value) ? `${(value * 100).toFixed(2)}%` : null;
}

function textSummary(summary) {
    return `
========== Outbox Scheduler Load Test ==========

target: ${summary.config.baseUrl}${summary.config.apiPrefix}
courseId: ${summary.config.courseId}
rate: ${summary.config.rate}
duration: ${summary.config.duration}

POST /courses/{courseId}/reports
  p50: ${summary.reportCreate.p50}
  p90: ${summary.reportCreate.p90}
  p95: ${summary.reportCreate.p95}
  p99: ${summary.reportCreate.p99}
  avg: ${summary.reportCreate.avg}
  max: ${summary.reportCreate.max}
  errorRate: ${summary.reportCreate.errorRate}
  serverErrors: ${summary.reportCreate.serverErrors}

requests: ${summary.requests.total}
rps: ${summary.requests.rps}

===============================================
`;
}
