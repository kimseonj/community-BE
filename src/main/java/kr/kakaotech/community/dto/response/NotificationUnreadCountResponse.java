package kr.kakaotech.community.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(name = "NotificationUnreadCountResponse", description = "안 읽은 알림 개수 응답")
@Getter
@AllArgsConstructor
public class NotificationUnreadCountResponse {
    @Schema(description = "안 읽은 알림 개수", example = "3")
    private long unreadCount;
}
