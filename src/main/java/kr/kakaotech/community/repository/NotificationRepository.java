package kr.kakaotech.community.repository;

import kr.kakaotech.community.dto.response.NotificationResponse;
import kr.kakaotech.community.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    long countByUser_IdAndCourseReport_Id(UUID userId, Long courseReportId);

    long countByUser_IdAndReadFalse(UUID userId);

    Optional<Notification> findByIdAndUser_Id(Long notificationId, UUID userId);

    @Query("""
            SELECT new kr.kakaotech.community.dto.response.NotificationResponse(
                n.id,
                n.title,
                n.content,
                n.read,
                cr.id,
                c.id,
                n.createdAt
            )
            FROM notifications n
            JOIN n.courseReport cr
            JOIN cr.course c
            WHERE n.user.id = :userId
            ORDER BY n.id DESC
            """)
    List<NotificationResponse> findNotificationsByUserId(
            @Param("userId") UUID userId,
            Pageable pageable
    );

    @Query("""
            SELECT new kr.kakaotech.community.dto.response.NotificationResponse(
                n.id,
                n.title,
                n.content,
                n.read,
                cr.id,
                c.id,
                n.createdAt
            )
            FROM notifications n
            JOIN n.courseReport cr
            JOIN cr.course c
            WHERE n.user.id = :userId
              AND n.id < :cursor
            ORDER BY n.id DESC
            """)
    List<NotificationResponse> findNotificationsByUserIdAndCursor(
            @Param("userId") UUID userId,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    @Query("select n.user.id from notifications n where n.eventId = :eventId")
    Set<UUID> findUserIdsByEventId(@Param("eventId") UUID eventId);
}
