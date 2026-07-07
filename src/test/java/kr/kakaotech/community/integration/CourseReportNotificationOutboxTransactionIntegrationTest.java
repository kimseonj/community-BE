package kr.kakaotech.community.integration;

import kr.kakaotech.community.dto.request.ReportRegisterRequest;
import kr.kakaotech.community.entity.Course;
import kr.kakaotech.community.entity.CourseReport;
import kr.kakaotech.community.entity.CourseReportType;
import kr.kakaotech.community.entity.CourseSubscription;
import kr.kakaotech.community.entity.EventOutbox;
import kr.kakaotech.community.entity.EventOutboxStatus;
import kr.kakaotech.community.entity.User;
import kr.kakaotech.community.repository.CourseReportRepository;
import kr.kakaotech.community.repository.CourseRepository;
import kr.kakaotech.community.repository.CourseSubscriptionRepository;
import kr.kakaotech.community.repository.EventOutboxRepository;
import kr.kakaotech.community.repository.NotificationRepository;
import kr.kakaotech.community.repository.UserRepository;
import kr.kakaotech.community.service.CourseReportNotificationOutboxService;
import kr.kakaotech.community.service.CourseReportNotificationOutboxTransactionService;
import kr.kakaotech.community.service.CourseReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
        "jwt.expirationtime.accessTtl=1800",
        "jwt.expirationtime.refreshTtl=604800",
        "jwt.secret=test-only-no-sensitive-secret-for-integration-test",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect"
})
@ActiveProfiles("test")
class CourseReportNotificationOutboxTransactionIntegrationTest {

    private static final String FAIL_TRIGGER = "fail_notification_insert";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.8")
            .withCommand("--log-bin-trust-function-creators=1");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    CourseReportService courseReportService;
    @Autowired
    CourseReportNotificationOutboxService outboxService;
    @Autowired
    CourseReportNotificationOutboxTransactionService transactionService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    CourseRepository courseRepository;
    @Autowired
    CourseReportRepository courseReportRepository;
    @Autowired
    CourseSubscriptionRepository courseSubscriptionRepository;
    @Autowired
    NotificationRepository notificationRepository;
    @Autowired
    EventOutboxRepository eventOutboxRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + FAIL_TRIGGER);
        notificationRepository.deleteAllInBatch();
        courseSubscriptionRepository.deleteAllInBatch();
        eventOutboxRepository.deleteAllInBatch();
        courseReportRepository.deleteAllInBatch();
        courseRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("제보 등록 시 CourseReport와 PENDING Outbox가 같은 트랜잭션에 저장된다")
    void registerReport_savesReportAndPendingOutbox() {
        User reporter = userRepository.save(new User("reporter@test.com", "password", "reporter", "USER"));
        Course course = courseRepository.save(new Course("한강종주"));
        ReportRegisterRequest request = new ReportRegisterRequest();
        ReflectionTestUtils.setField(request, "type", CourseReportType.CONSTRUCTION);
        ReflectionTestUtils.setField(request, "content", "강변 진입로 일부 공사 중입니다.");

        CourseReport report = courseReportService.registerReport(course.getId(), request, reporter.getId());

        assertThat(courseReportRepository.findById(report.getId())).isPresent();
        assertThat(eventOutboxRepository.findAll())
                .singleElement()
                .satisfies(outbox -> {
                    assertThat(outbox.getAggregateId()).isEqualTo(report.getId());
                    assertThat(outbox.getStatus()).isEqualTo(EventOutboxStatus.PENDING);
                });
    }

    @Test
    @DisplayName("claim과 실패 기록은 독립 커밋되며 세 번째 실패는 FAILED가 된다")
    void claimAndFailure_areCommittedUntilMaxRetry() {
        EventOutbox outbox = eventOutboxRepository.saveAndFlush(newOutbox());

        for (int retryCount = 1; retryCount <= 3; retryCount++) {
            var claim = transactionService.claimNext().orElseThrow();
            EventOutbox claimed = eventOutboxRepository.findById(outbox.getId()).orElseThrow();
            assertThat(claimed.getStatus()).isEqualTo(EventOutboxStatus.PROCESSING);
            assertThat(claimed.getProcessingStartedAt()).isEqualTo(claim.processingStartedAt());

            var failure = transactionService.recordFailure(claim).orElseThrow();
            EventOutbox failed = eventOutboxRepository.findById(outbox.getId()).orElseThrow();
            assertThat(failed.getRetryCount()).isEqualTo(retryCount);
            assertThat(failed.getStatus()).isEqualTo(retryCount == 3
                    ? EventOutboxStatus.FAILED
                    : EventOutboxStatus.PENDING);
            assertThat(failure.status()).isEqualTo(failed.getStatus());

            if (retryCount < 3) {
                assertThat(failed.getNextRetryAt()).isAfter(LocalDateTime.now());
                jdbcTemplate.update(
                        "UPDATE event_outbox SET next_retry_at = DATE_SUB(NOW(3), INTERVAL 1 SECOND) WHERE id = ?",
                        outbox.getId()
                );
            } else {
                assertThat(failed.getNextRetryAt()).isNull();
            }
        }
    }

    @Test
    @DisplayName("실패한 다음 chunk만 rollback되고 재처리 후 중복 없이 모든 알림이 저장된다")
    void chunkFailure_retryCompletesWithoutDuplicates() {
        User reporter = userRepository.save(new User("reporter@test.com", "password", "reporter", "USER"));
        Course course = courseRepository.save(new Course("한강종주"));
        CourseReport report = courseReportRepository.save(new CourseReport(
                course,
                reporter,
                CourseReportType.CLOSED,
                "진입로가 통제되었습니다."
        ));

        List<User> subscribers = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            subscribers.add(new User("sub%03d@test.com".formatted(i), "password", "sub%03d".formatted(i), "USER"));
        }
        userRepository.saveAll(subscribers);
        courseSubscriptionRepository.saveAll(subscribers.stream()
                .map(subscriber -> new CourseSubscription(subscriber, course))
                .toList());
        EventOutbox outbox = eventOutboxRepository.saveAndFlush(new EventOutbox(
                "COURSE_REPORT_CREATED",
                "COURSE_REPORT",
                report.getId(),
                "{}"
        ));
        UUID failedUserId = subscribers.getLast().getId();
        createFailureTrigger(failedUserId);

        outboxService.processPendingCourseReportCreatedEvent();

        assertThat(notificationRepository.count()).isEqualTo(1000);
        assertThat(notificationCount(outbox.getEventId(), failedUserId)).isZero();
        EventOutbox failed = eventOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(EventOutboxStatus.PENDING);
        assertThat(failed.getRetryCount()).isOne();

        jdbcTemplate.execute("DROP TRIGGER " + FAIL_TRIGGER);
        jdbcTemplate.update(
                "UPDATE event_outbox SET next_retry_at = DATE_SUB(NOW(3), INTERVAL 1 SECOND) WHERE id = ?",
                outbox.getId()
        );
        outboxService.processPendingCourseReportCreatedEvent();

        assertThat(notificationRepository.count()).isEqualTo(1001);
        assertThat(duplicateNotificationCount(outbox.getEventId())).isZero();
        assertThat(eventOutboxRepository.findById(outbox.getId()).orElseThrow().getStatus())
                .isEqualTo(EventOutboxStatus.PROCESSED);
    }

    @Test
    @DisplayName("두 워커가 동시에 claim해도 하나의 워커만 이벤트를 획득한다")
    void concurrentClaim_onlyOneWorkerClaimsEvent() throws Exception {
        eventOutboxRepository.saveAndFlush(newOutbox());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> claimAfterSignal(ready, start));
            var second = executor.submit(() -> claimAfterSignal(ready, start));
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .filteredOn(Optional::isPresent)
                    .hasSize(1);
        }
    }

    @Test
    @DisplayName("lease가 만료된 PROCESSING 이벤트는 다시 claim된다")
    void expiredProcessingLease_isClaimedAgain() {
        EventOutbox outbox = eventOutboxRepository.saveAndFlush(newOutbox());
        var expiredClaim = transactionService.claimNext().orElseThrow();
        jdbcTemplate.update(
                "UPDATE event_outbox SET processing_started_at = DATE_SUB(NOW(3), INTERVAL 6 MINUTE) WHERE id = ?",
                outbox.getId()
        );

        var renewedClaim = transactionService.claimNext().orElseThrow();

        assertThat(renewedClaim.outboxId()).isEqualTo(expiredClaim.outboxId());
        assertThat(renewedClaim.processingStartedAt()).isAfter(expiredClaim.processingStartedAt());
        assertThat(transactionService.markProcessed(expiredClaim)).isFalse();
        assertThat(transactionService.markProcessed(renewedClaim)).isTrue();
        assertThat(eventOutboxRepository.findById(outbox.getId()).orElseThrow().getStatus())
                .isEqualTo(EventOutboxStatus.PROCESSED);
    }

    private EventOutbox newOutbox() {
        return new EventOutbox("COURSE_REPORT_CREATED", "COURSE_REPORT", 99L, "{}");
    }

    private Optional<CourseReportNotificationOutboxTransactionService.Claim> claimAfterSignal(
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return transactionService.claimNext();
    }

    private void createFailureTrigger(UUID userId) {
        jdbcTemplate.execute("""
                CREATE TRIGGER %s
                BEFORE INSERT ON notifications
                FOR EACH ROW
                BEGIN
                    IF NEW.user_id = UUID_TO_BIN('%s') THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced chunk failure';
                    END IF;
                END
                """.formatted(FAIL_TRIGGER, userId));
    }

    private long notificationCount(UUID eventId, UUID userId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM notifications
                WHERE event_id = UUID_TO_BIN(?)
                  AND user_id = UUID_TO_BIN(?)
                """, Long.class, eventId.toString(), userId.toString());
    }

    private long duplicateNotificationCount(UUID eventId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) - COUNT(DISTINCT user_id)
                FROM notifications
                WHERE event_id = UUID_TO_BIN(?)
                """, Long.class, eventId.toString());
    }
}
