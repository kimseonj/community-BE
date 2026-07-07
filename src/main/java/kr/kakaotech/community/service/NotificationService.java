package kr.kakaotech.community.service;

import kr.kakaotech.community.dto.response.NotificationListResponse;
import kr.kakaotech.community.dto.response.NotificationResponse;
import kr.kakaotech.community.dto.response.NotificationUnreadCountResponse;
import kr.kakaotech.community.entity.CourseReport;
import kr.kakaotech.community.entity.Notification;
import kr.kakaotech.community.exception.CustomException;
import kr.kakaotech.community.exception.ErrorCode;
import kr.kakaotech.community.repository.CourseSubscriptionRepository;
import kr.kakaotech.community.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private static final int TITLE_MAX_LENGTH = 100;
    private static final int CONTENT_MAX_LENGTH = 500;
    private static final int MAX_PAGE_SIZE = 20;
    private static final int NOTIFICATION_CHUNK_SIZE = 1_000;
    private static final PageRequest NOTIFICATION_CHUNK_PAGE = PageRequest.of(0, NOTIFICATION_CHUNK_SIZE);

    private final CourseSubscriptionRepository courseSubscriptionRepository;
    private final NotificationRepository notificationRepository;
    private final CourseReportNotificationOutboxTransactionService transactionService;

    @Transactional(readOnly = true)
    public NotificationUnreadCountResponse getUnreadCount(UUID userId) {
        return new NotificationUnreadCountResponse(notificationRepository.countByUser_IdAndReadFalse(userId));
    }

    @Transactional(readOnly = true)
    public NotificationListResponse getNotifications(UUID userId, Long cursor, int size) {
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        PageRequest pageRequest = PageRequest.of(0, size + 1);
        List<NotificationResponse> notifications = cursor == null
                ? notificationRepository.findNotificationsByUserId(userId, pageRequest)
                : notificationRepository.findNotificationsByUserIdAndCursor(userId, cursor, pageRequest);

        boolean hasNext = notifications.size() > size;
        List<NotificationResponse> page = hasNext ? notifications.subList(0, size) : notifications;
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;

        return new NotificationListResponse(page, nextCursor, hasNext);
    }

    @Transactional
    public void markAsRead(UUID userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUser_Id(notificationId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND_NOTIFICATION));

        notification.markAsRead();
    }

    public void createCourseReportNotifications(CourseReport report, UUID eventId) {
        long startedAt = System.nanoTime();
        long lastSubscriptionId = 0;
        long subscriberCount = 0;
        int chunkCount = 0;
        String title = createTitle(report);
        String content = createContent(report);

        while (true) {
            var subscribers = courseSubscriptionRepository.findSubscriberChunk(
                    report.getCourse().getId(),
                    lastSubscriptionId,
                    NOTIFICATION_CHUNK_PAGE
            );
            if (subscribers.isEmpty()) {
                break;
            }

            transactionService.insertNotificationChunk(
                    subscribers.stream().map(CourseSubscriptionRepository.SubscriberProjection::getUserId).toList(),
                    report.getId(),
                    eventId,
                    title,
                    content
            );
            lastSubscriptionId = subscribers.getLast().getSubscriptionId();
            subscriberCount += subscribers.size();
            chunkCount++;
        }

        log.info(
                "Course report notifications processed. eventId={}, subscriberCount={}, chunkCount={}, durationMs={}",
                eventId,
                subscriberCount,
                chunkCount,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        );
    }

    private String createTitle(CourseReport report) {
        return truncate("코스 상태 제보: " + report.getCourse().getName(), TITLE_MAX_LENGTH);
    }

    private String createContent(CourseReport report) {
        return truncate(report.getType().getDescription() + " - " + report.getContent(), CONTENT_MAX_LENGTH);
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength - 3) + "...";
    }
}
