package kr.kakaotech.community.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.kakaotech.community.auth.AuthUser;
import kr.kakaotech.community.auth.CurrentUser;
import kr.kakaotech.community.dto.ApiResponse;
import kr.kakaotech.community.dto.response.LikeResponse;
import kr.kakaotech.community.dto.response.PostTypeCountResponse;
import kr.kakaotech.community.service.LikeService;
import kr.kakaotech.community.service.PostStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Tag(name = "PostStatus", description = "좋아요 및 게시글 통계 API")
@Slf4j
@RequiredArgsConstructor
@Controller
public class PostStatusController {
    private final LikeService likeService;
    private final PostStatusService postStatusService;

    @Operation(summary = "좋아요 토글", description = "게시글 좋아요를 토글합니다. 좋아요가 없으면 추가, 있으면 삭제됩니다.")
    @PostMapping("/posts/{postId}/likes")
    public ResponseEntity<ApiResponse<LikeResponse>> toggleLike(@PathVariable int postId, @CurrentUser AuthUser authUser) {
        return ApiResponse.success("좋아요 토글 성공", likeService.toggleLike(authUser.userId(), postId));
    }

    @Operation(summary = "좋아요 상태 조회", description = "현재 사용자의 좋아요 여부와 전체 좋아요 수를 조회합니다.")
    @GetMapping("/posts/{postId}/likes")
    public ResponseEntity<ApiResponse<LikeResponse>> getLikeStatus(@PathVariable int postId,
                                                                   @CurrentUser(required = false) AuthUser authUser) {
        LikeResponse likeResponse = new LikeResponse(
                likeService.getLikeStatus(Optional.ofNullable(authUser).map(AuthUser::userId), postId),
                likeService.getLikeCount(postId)
        );
        return ApiResponse.success("좋아요 상태", likeResponse);
    }

    @Operation(summary = "게시글 타입별 수 조회", description = "IN_PROGRESS/COMPLETED 타입별 게시글 수를 조회합니다.")
    @GetMapping("/posts/type")
    public ResponseEntity<ApiResponse<PostTypeCountResponse>> getTypeStatus(
            @Parameter(description = "게시글 타입 (IN_PROGRESS/COMPLETED)") @RequestParam String type) {
        return ApiResponse.success("해당 타입의 게시글 수 입니다.", postStatusService.getPostTypeCount(type));
    }
}
