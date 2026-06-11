package kr.kakaotech.community.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@Entity(name = "notifications")
@Table(name = "notifications",
        uniqueConstraints = @UniqueConstraint(name = "uk_notifications_event_user", columnNames = {"event_id", "user_id"}),
        indexes = @Index(name = "idx_notifications_user_read_created", columnList = "user_id, is_read, created_at")
)
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_report_id", nullable = false)
    private CourseReport courseReport;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "is_read", nullable = false)
    private Boolean read = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Notification(User user, CourseReport courseReport, UUID eventId, String title, String content) {
        this.user = user;
        this.courseReport = courseReport;
        this.eventId = eventId;
        this.title = title;
        this.content = content;
    }

    public void markAsRead() {
        this.read = true;
    }
}
