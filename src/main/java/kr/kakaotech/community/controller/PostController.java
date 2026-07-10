package kr.kakaotech.community.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.kakaotech.community.auth.AuthUser;
import kr.kakaotech.community.auth.CurrentUser;
import kr.kakaotech.community.dto.ApiResponse;
import kr.kakaotech.community.dto.request.PostModifyRequest;
import kr.kakaotech.community.dto.request.PostRegisterRequest;
import kr.kakaotech.community.dto.response.PostDetailResponse;
import kr.kakaotech.community.dto.response.PostListResponse;
import kr.kakaotech.community.dto.response.PostStatusResponse;
import kr.kakaotech.community.dto.response.PostSummaryWithImageResponse;
import kr.kakaotech.community.service.PostService;
import kr.kakaotech.community.service.PostStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Post", description = "게시글 API")
@Slf4j
@RequiredArgsConstructor
@RestController
public class PostController {

    private final PostService postService;
    private final PostStatusService postStatusService;

    @Operation(summary = "게시글 작성", description = "게시글을 작성합니다. 이미지는 최대 5장까지 첨부 가능합니다.")
    @PostMapping("/posts")
    public ResponseEntity<ApiResponse<Integer>> registerPost(@CurrentUser AuthUser authUser,
                                                             @Valid @ModelAttribute PostRegisterRequest postRegisterRequest,
                                                             @RequestPart(value = "postImages", required = false) List<MultipartFile> images) {
        return ApiResponse.create("게시글 등록 성공", postService.registerPost(authUser.userId().toString(), postRegisterRequest, images));
    }

    @Operation(summary = "게시글 목록 조회", description = "커서 기반 페이지네이션으로 게시글 목록을 조회합니다. nickname, period 파라미터로 필터링 가능합니다.")
    @GetMapping("/posts")
    public ResponseEntity<ApiResponse<PostListResponse>> getPostList(
            @Parameter(description = "다음 페이지 커서 (게시글 ID)") @RequestParam(required = false) Integer cursor,
            @Parameter(description = "작성자 닉네임 필터") @RequestParam(required = false) String nickname,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "5") int size,
            @Parameter(description = "기간 필터 (daily/weekly)") @RequestParam(required = false) String period) {
        String wanted = "";
        PostListResponse response = null;
        if (period == null && nickname == null) {
            response = postService.getPostList(cursor, size);
        } else if (nickname == null) {
            wanted = "Like - ";
            response = postService.getLikePostList(cursor, period, size);
        }
        if (nickname != null) {
            wanted = "nickname - ";
            response = postService.getNicknamePostList(cursor, nickname, size);
        }

        return ApiResponse.success(wanted + "게시글 목록 조회 성공", response);
    }

    @Operation(summary = "인기 게시글 Top10", description = "좋아요 기준 상위 10개 완료 게시글을 조회합니다.")
    @GetMapping("/posts/top10")
    public ResponseEntity<ApiResponse<PostListResponse>> getPostList() {
        return ApiResponse.success("게시글 목록 조회 성공", postService.getPostTop10List());
    }

    @Operation(summary = "인덱스 게시글 목록", description = "홈 화면용 최신 게시글 3건을 이미지와 함께 조회합니다.")
    @GetMapping("/posts/index")
    public ResponseEntity<ApiResponse<List<PostSummaryWithImageResponse>>> getIndexPostList() {
        return ApiResponse.success("인덱스 게시글 목록 조회 성공", postService.getPostListWithImage(3));
    }

    @Operation(summary = "게시글 상세 조회", description = "게시글 ID로 상세 정보를 조회합니다.")
    @GetMapping("/posts/{postId}")
    public ResponseEntity<ApiResponse<PostDetailResponse>> getPost(@PathVariable int postId) {
        PostDetailResponse response = postService.getPostDetails(postId);
        // 조회수 증가
//        postStatusService.incrementViewCountRDB(postId);

        return ApiResponse.success("게시글 상세 내용입니다.", response);
    }

    @Operation(summary = "게시글 통계 조회", description = "게시글의 조회수 등 통계 정보를 조회합니다.")
    @GetMapping("/posts/{postId}/statuses")
    public ResponseEntity<ApiResponse<PostStatusResponse>> getPostStatus(@PathVariable int postId) {
        PostStatusResponse postStatusResponse = postStatusService.getPostStatus(postId);

        return ApiResponse.success("게시글 Status 입니다.", postStatusResponse);
    }

    @Operation(summary = "게시글 수정", description = "게시글을 수정합니다. 이미지 추가/삭제가 가능합니다.")
    @PatchMapping("/posts/{postId}")
    public ResponseEntity<ApiResponse<Object>> updatePost(@PathVariable int postId,
                                                           @CurrentUser AuthUser authUser,
                                                           @Valid @ModelAttribute PostModifyRequest postModifyRequest,
                                                           @RequestPart(value = "postImages", required = false) List<MultipartFile> images) {
        postService.updatePost(postId, authUser.userId().toString(), postModifyRequest, images);

        return ApiResponse.success("게시글 수정 성공", null);
    }

    @Operation(summary = "게시글 삭제", description = "게시글을 소프트 삭제합니다.")
    @PatchMapping("/posts/{postId}/deactivation")
    public ResponseEntity<ApiResponse<Object>> deactivatePost(@PathVariable int postId, @CurrentUser AuthUser authUser) {
        postService.deletePost(postId, authUser.userId().toString());

        return ApiResponse.success("삭제 성공", null);
    }

    @Operation(summary = "게시글 상태 동기화", description = "게시글 상태 카운터를 동기화합니다.")
    @PostMapping("/post-status")
    public ResponseEntity<ApiResponse<Object>> syncPostStatus() {
        return ApiResponse.success("싱크 성공", null);
    }
}
