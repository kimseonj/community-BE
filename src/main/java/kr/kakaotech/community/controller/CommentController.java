package kr.kakaotech.community.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.kakaotech.community.auth.AuthUser;
import kr.kakaotech.community.auth.CurrentUser;
import kr.kakaotech.community.dto.ApiResponse;
import kr.kakaotech.community.dto.request.CommentRequest;
import kr.kakaotech.community.dto.response.CommentResponse;
import kr.kakaotech.community.service.CommentService;
import kr.kakaotech.community.service.PostStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Comment", description = "댓글 API")
@RequiredArgsConstructor
@RestController
public class CommentController {

    private final CommentService commentService;
    private final PostStatusService postStatusService;

    @Operation(summary = "댓글 등록", description = "게시글에 댓글을 등록합니다.")
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<Void>> registerComment(@PathVariable int postId, @CurrentUser AuthUser authUser, @Valid @RequestBody CommentRequest request) {
        commentService.registerComment(authUser.userId().toString(), postId, request);

        ApiResponse<Void> apiResponse = new ApiResponse<>("댓글 등록 성공", null);
        return ResponseEntity.status(201).body(apiResponse);
    }

    @Operation(summary = "댓글 목록 조회", description = "게시글의 댓글 목록을 페이징으로 조회합니다.")
    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<Page<CommentResponse>>> getCommentList(
            @PathVariable int postId,
            @PageableDefault(size = 100, sort = "createdAt") Pageable pageable
    ) {
        Page<CommentResponse> response = commentService.getCommentList(postId, pageable);

        ApiResponse<Page<CommentResponse>> apiResponse = new ApiResponse<>("댓글 목록 조회 성공", response);
        return ResponseEntity.ok(apiResponse);
    }

    @Operation(summary = "댓글 수정", description = "댓글 내용을 수정합니다.")
    @PatchMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> updateComment(@PathVariable int commentId, @CurrentUser AuthUser authUser, @Valid @RequestBody CommentRequest request) {
        commentService.updateComment(authUser.userId().toString(), commentId, request);

        ApiResponse<Void> apiResponse = new ApiResponse<>("댓글 수정 성공", null);
        return ResponseEntity.ok(apiResponse);
    }

    @Operation(summary = "댓글 삭제", description = "댓글을 소프트 삭제합니다.")
    @PatchMapping("/comments/{commentId}/deactivation")
    public ResponseEntity<ApiResponse<Void>> deleteComment(@PathVariable int commentId, @CurrentUser AuthUser authUser) {
        commentService.deleteComment(authUser.userId().toString(), commentId);

        ApiResponse<Void> apiResponse = new ApiResponse<>("댓글 삭제 성공", null);
        return ResponseEntity.ok(apiResponse);
    }
}

