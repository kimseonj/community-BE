package kr.kakaotech.community.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Schema(name = "NotificationResponse", description = "인앱 알림 응답")
@Getter
@AllArgsConstructor
public class NotificationResponse {
    @Schema(description = "알림 ID", example = "1")
    private Long id;

    @Schema(description = "알림 제목", example = "코스 상태 제보: 한강종주")
    private String title;

    @Schema(description = "알림 내용", example = "통제 - 강변 진입로 일부 공사 중입니다.")
    private String content;

    @Schema(description = "읽음 여부", example = "false")
    private Boolean read;

    @Schema(description = "연결된 코스 제보 ID", example = "10")
    private Long courseReportId;

    @Schema(description = "연결된 코스 ID", example = "1")
    private Integer courseId;

    @Schema(description = "알림 생성 시각", example = "2026-06-08T10:30:00")
    private LocalDateTime createdAt;
}
