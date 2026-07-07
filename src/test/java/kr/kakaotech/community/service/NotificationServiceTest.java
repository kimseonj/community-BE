package kr.kakaotech.community.service;

import kr.kakaotech.community.entity.Course;
import kr.kakaotech.community.entity.CourseReport;
import kr.kakaotech.community.entity.CourseReportType;
import kr.kakaotech.community.entity.Notification;
import kr.kakaotech.community.entity.User;
import kr.kakaotech.community.dto.response.NotificationListResponse;
import kr.kakaotech.community.dto.response.NotificationResponse;
import kr.kakaotech.community.exception.CustomException;
import kr.kakaotech.community.exception.ErrorCode;
import kr.kakaotech.community.repository.CourseSubscriptionRepository;
import kr.kakaotech.community.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    CourseSubscriptionRepository courseSubscriptionRepository;
    @Mock
    NotificationRepository notificationRepository;
    @Mock
    CourseReportNotificationOutboxTransactionService transactionService;

    @Test
    @DisplayName("안 읽은 알림 개수를 사용자 기준으로 조회한다")
    void getUnreadCount_success() {
        // given
        UUID userId = UUID.randomUUID();
        NotificationService notificationService = notificationService();

        given(notificationRepository.countByUser_IdAndReadFalse(userId)).willReturn(3L);

        // when
        long unreadCount = notificationService.getUnreadCount(userId).getUnreadCount();

        // then
        assertThat(unreadCount).isEqualTo(3L);
    }

    @Test
    @DisplayName("내 알림 목록을 커서 기반으로 조회하고 다음 커서를 반환한다")
    void getNotifications_success_cursorPaging() {
        // given
        UUID userId = UUID.randomUUID();
        NotificationService notificationService = notificationService();
        List<NotificationResponse> repositoryResults = List.of(
                new NotificationResponse(3L, "제목3", "내용3", false, 30L, 1, LocalDateTime.now()),
                new NotificationResponse(2L, "제목2", "내용2", false, 20L, 1, LocalDateTime.now()),
                new NotificationResponse(1L, "제목1", "내용1", false, 10L, 1, LocalDateTime.now())
        );

        given(notificationRepository.findNotificationsByUserId(eq(userId), any(PageRequest.class)))
                .willReturn(repositoryResults);

        // when
        NotificationListResponse response = notificationService.getNotifications(userId, null, 2);

        // then
        assertThat(response.getNotifications())
                .extracting(NotificationResponse::getId)
                .containsExactly(3L, 2L);
        assertThat(response.getNextCursor()).isEqualTo(2L);
        assertThat(response.isHasNext()).isTrue();
    }

    @Test
    @DisplayName("내 알림 목록 조회 페이지 크기는 1 이상 20 이하만 허용한다")
    void getNotifications_fail_invalidSize() {
        // given
        UUID userId = UUID.randomUUID();
        NotificationService notificationService = notificationService();

        // when & then
        assertThatThrownBy(() -> notificationService.getNotifications(userId, null, 0))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> notificationService.getNotifications(userId, null, 21))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("알림 읽음 처리는 멱등하게 읽음 상태로 변경한다")
    void markAsRead_success_idempotent() {
        // given
        UUID userId = UUID.randomUUID();
        Course course = new Course("한강종주");
        User user = new User("user@test.com", "password", "user", "USER");
        CourseReport report = new CourseReport(
                course,
                user,
                CourseReportType.CLOSED,
                "진입로가 통제되었습니다."
        );
        Notification notification = new Notification(user, report, UUID.randomUUID(), "제목", "내용");
        NotificationService notificationService = notificationService();

        given(notificationRepository.findByIdAndUser_Id(1L, userId))
                .willReturn(Optional.of(notification));

        // when
        notificationService.markAsRead(userId, 1L);
        notificationService.markAsRead(userId, 1L);

        // then
        assertThat(notification.getRead()).isTrue();
    }

    @Test
    @DisplayName("내 알림이 아니면 읽음 처리할 알림을 찾을 수 없다")
    void markAsRead_fail_notFound() {
        // given
        UUID userId = UUID.randomUUID();
        NotificationService notificationService = notificationService();

        given(notificationRepository.findByIdAndUser_Id(1L, userId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> notificationService.markAsRead(userId, 1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND_NOTIFICATION);
    }

    @Test
    @DisplayName("구독자를 1,000건씩 keyset 조회하고 chunk 저장을 위임한다")
    void createCourseReportNotifications_processesKeysetChunks() {
        // given
        Course course = new Course("한강종주");
        ReflectionTestUtils.setField(course, "id", 1);
        User reporter = new User("reporter@test.com", "password", "reporter", "USER");
        CourseReport report = new CourseReport(
                course,
                reporter,
                CourseReportType.CLOSED,
                "진입로가 통제되었습니다."
        );
        ReflectionTestUtils.setField(report, "id", 99L);
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID thirdUserId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        NotificationService notificationService = notificationService();
        PageRequest chunkPage = PageRequest.of(0, 1_000);

        given(courseSubscriptionRepository.findSubscriberChunk(1, 0L, chunkPage))
                .willReturn(List.of(
                        new Subscriber(10L, firstUserId),
                        new Subscriber(20L, secondUserId)
                ));
        given(courseSubscriptionRepository.findSubscriberChunk(1, 20L, chunkPage))
                .willReturn(List.of(new Subscriber(30L, thirdUserId)));
        given(courseSubscriptionRepository.findSubscriberChunk(1, 30L, chunkPage))
                .willReturn(List.of());

        // when
        notificationService.createCourseReportNotifications(report, eventId);

        // then
        verify(transactionService).insertNotificationChunk(
                List.of(firstUserId, secondUserId),
                99L,
                eventId,
                "코스 상태 제보: 한강종주",
                "통제 - 진입로가 통제되었습니다."
        );
        verify(transactionService).insertNotificationChunk(
                List.of(thirdUserId),
                99L,
                eventId,
                "코스 상태 제보: 한강종주",
                "통제 - 진입로가 통제되었습니다."
        );
        verify(courseSubscriptionRepository).findSubscriberChunk(1, 30L, chunkPage);
    }

    @Test
    @DisplayName("구독자가 없으면 chunk 저장을 호출하지 않는다")
    void createCourseReportNotifications_noSubscribers() {
        // given
        Course course = new Course("한강종주");
        ReflectionTestUtils.setField(course, "id", 1);
        CourseReport report = new CourseReport(
                course,
                new User("reporter@test.com", "password", "reporter", "USER"),
                CourseReportType.CLOSED,
                "진입로가 통제되었습니다."
        );
        UUID eventId = UUID.randomUUID();
        NotificationService notificationService = notificationService();
        given(courseSubscriptionRepository.findSubscriberChunk(1, 0L, PageRequest.of(0, 1_000)))
                .willReturn(List.of());

        // when
        notificationService.createCourseReportNotifications(report, eventId);

        // then
        verifyNoInteractions(transactionService);
    }

    private NotificationService notificationService() {
        return new NotificationService(
                courseSubscriptionRepository,
                notificationRepository,
                transactionService
        );
    }

    private record Subscriber(Long subscriptionId, UUID userId)
            implements CourseSubscriptionRepository.SubscriberProjection {
        @Override
        public Long getSubscriptionId() {
            return subscriptionId;
        }

        @Override
        public UUID getUserId() {
            return userId;
        }
    }
}
