package kr.kakaotech.community.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import kr.kakaotech.community.auth.AuthUser;
import kr.kakaotech.community.auth.CurrentUser;
import kr.kakaotech.community.dto.ApiResponse;
import kr.kakaotech.community.dto.request.UserPasswordRequest;
import kr.kakaotech.community.dto.request.UserRegisterRequest;
import kr.kakaotech.community.dto.request.UserUpdateRequest;
import kr.kakaotech.community.dto.response.UserDetailResponse;
import kr.kakaotech.community.exception.CustomException;
import kr.kakaotech.community.exception.ErrorCode;
import kr.kakaotech.community.service.AuthService;
import kr.kakaotech.community.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "User", description = "회원 API")
@RequiredArgsConstructor
@RestController
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    @Operation(summary = "회원가입", description = "이메일, 닉네임, 비밀번호로 회원가입합니다. 프로필 이미지 첨부 가능합니다.")
    @PostMapping(value = "/users", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<String>> register(@Valid @ModelAttribute UserRegisterRequest userDto,
                                                        @RequestPart(value = "profileImage", required = false) MultipartFile image) {
        userService.registerUser(userDto, image);

        return ApiResponse.create("회원가입 성공", userDto.getEmail());
    }

    @Operation(summary = "회원 상세 조회", description = "회원 ID로 상세 정보를 조회합니다.")
    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<UserDetailResponse>> getUser(@PathVariable String userId) {
        UserDetailResponse userDetailResponse = userService.getUser(userId);

        return ApiResponse.success("단일 회원 조회 성공", userDetailResponse);
    }

    @Operation(summary = "회원 목록 조회", description = "페이징된 회원 목록을 조회합니다.")
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<UserDetailResponse>>> getUserList(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        Page<UserDetailResponse> userPage = userService.getUserPage(pageable);

        ApiResponse<Page<UserDetailResponse>> response = new ApiResponse<>("모든 회원 불러오기 성공", userPage);
        return ResponseEntity.status(200).body(response);
    }

    @Operation(summary = "회원 정보 수정", description = "닉네임, 프로필 이미지를 수정합니다.")
    @PatchMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<UserDetailResponse>> updateUser(@PathVariable String userId,
                                                                      @CurrentUser AuthUser authUser,
                                                                      @Valid @ModelAttribute UserUpdateRequest userUpdateRequest,
                                                                      @RequestPart(value = "profileImage", required = false) MultipartFile image) {
        if (!userId.equals(authUser.userId().toString())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        UserDetailResponse userDetailResponse = userService.updateUser(userId, userUpdateRequest, image);

        ApiResponse<UserDetailResponse> apiResponse = new ApiResponse<>("업데이트 성공", userDetailResponse);
        return ResponseEntity.ok(apiResponse);
    }

    @Operation(summary = "회원 탈퇴", description = "회원을 소프트 삭제합니다. 닉네임 변경 및 쿠키가 삭제됩니다.")
    @PatchMapping("/users/{userId}/deactivation")
    public void deleteUser(@PathVariable String userId,
                           @CurrentUser AuthUser authUser,
                           @Valid @RequestBody UserPasswordRequest userPasswordRequest,
                           HttpServletRequest request,
                           HttpServletResponse response) {
        userService.softDeleteUser(userId, authUser.userId().toString(), userPasswordRequest.getCurrentPassword());
        authService.deleteAuth(request, response);
    }

    @Operation(summary = "이메일 중복 확인", description = "이메일 중복 여부를 실시간 검증합니다.")
    @GetMapping("/users/email")
    public ResponseEntity<ApiResponse<Boolean>> checkUserEmail(@RequestParam String email) {
        return ApiResponse.success("duplication 결과", userService.duplicateCheckUserInfo("email", email));
    }

    @Operation(summary = "닉네임 중복 확인", description = "닉네임 중복 여부를 실시간 검증합니다.")
    @GetMapping("/users/nickname")
    public ResponseEntity<ApiResponse<Boolean>> checkUserNickname(@RequestParam String nickname) {
        return ApiResponse.success("duplication 결과", userService.duplicateCheckUserInfo("nickname", nickname));
    }

    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호 확인 후 새 비밀번호로 변경합니다. 변경 후 재로그인이 필요합니다.")
    @PatchMapping("/users/password")
    public ResponseEntity<ApiResponse<Boolean>> changePassword(@CurrentUser AuthUser authUser,
                                                               @Valid @RequestBody UserPasswordRequest userPasswordRequest,
                                                               HttpServletRequest request,
                                                               HttpServletResponse response) {
        boolean isChangePassword = userService.changePassword(authUser.userId().toString(), userPasswordRequest);

        authService.deleteAuth(request, response);
        return ApiResponse.success("비밀번호 수정 결과", isChangePassword);
    }
}
