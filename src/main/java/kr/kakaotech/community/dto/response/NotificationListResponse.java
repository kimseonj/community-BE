package kr.kakaotech.community.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Schema(name = "NotificationListResponse", description = "내 인앱 알림 목록 응답")
@Getter
@AllArgsConstructor
public class NotificationListResponse {
    @Schema(description = "알림 목록")
    private List<NotificationResponse> notifications;

    @Schema(description = "다음 페이지 커서. 다음 페이지가 없으면 null", example = "10")
    private Long nextCursor;

    @Schema(description = "다음 페이지 존재 여부", example = "true")
    private boolean hasNext;
}
