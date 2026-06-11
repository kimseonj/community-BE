package kr.kakaotech.community.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import kr.kakaotech.community.dto.ApiResponse;
import kr.kakaotech.community.dto.response.NotificationListResponse;
import kr.kakaotech.community.dto.response.NotificationUnreadCountResponse;
import kr.kakaotech.community.exception.CustomException;
import kr.kakaotech.community.exception.ErrorCode;
import kr.kakaotech.community.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Notification", description = "인앱 알림 조회 및 읽음 처리 API")
@RequestMapping("/me/notifications")
@RequiredArgsConstructor
@RestController
public class NotificationController {
    private static final int MAX_PAGE_SIZE = 20;

    private final NotificationService notificationService;

    @Operation(
            summary = "안 읽은 알림 개수 조회",
            description = "현재 로그인한 사용자의 안 읽은 인앱 알림 개수를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "안 읽은 알림 개수 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<NotificationUnreadCountResponse>> getUnreadCount(
            @Parameter(hidden = true) HttpServletRequest request) {
        UUID userId = UUID.fromString(request.getAttribute("userId").toString());

        return ApiResponse.success("안 읽은 알림 개수 조회 성공", notificationService.getUnreadCount(userId));
    }

    @Operation(
            summary = "내 알림 목록 조회",
            description = "현재 로그인한 사용자의 인앱 알림 목록을 커서 기반 페이지네이션으로 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "알림 목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<NotificationListResponse>> getNotifications(
            @Parameter(description = "다음 페이지 커서 (알림 ID)") @RequestParam(required = false) Long cursor,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size,
            @Parameter(hidden = true) HttpServletRequest request) {
        UUID userId = UUID.fromString(request.getAttribute("userId").toString());
        validatePageSize(size);

        return ApiResponse.success("알림 목록 조회 성공", notificationService.getNotifications(userId, cursor, size));
    }

    @Operation(
            summary = "알림 읽음 처리",
            description = "현재 로그인한 사용자의 특정 인앱 알림을 읽음 처리합니다. 이미 읽은 알림도 멱등하게 성공합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "알림 읽음 처리 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "알림을 찾을 수 없음")
    })
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @Parameter(description = "읽음 처리할 알림 ID", example = "1", required = true)
            @PathVariable Long notificationId,
            @Parameter(hidden = true) HttpServletRequest request) {
        UUID userId = UUID.fromString(request.getAttribute("userId").toString());
        validateNotificationId(notificationId);

        notificationService.markAsRead(userId, notificationId);

        return ApiResponse.success("알림 읽음 처리 성공", null);
    }

    private void validatePageSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    private void validateNotificationId(Long notificationId) {
        if (notificationId == null || notificationId < 1) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }
}
