package kr.kakaotech.community.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.kakaotech.community.auth.AuthUser;
import kr.kakaotech.community.auth.CurrentUser;
import kr.kakaotech.community.dto.ApiResponse;
import kr.kakaotech.community.dto.request.ReportRegisterRequest;
import kr.kakaotech.community.dto.response.CourseResponse;
import kr.kakaotech.community.service.CourseReportCommandService;
import kr.kakaotech.community.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Course", description = "국토종주 코스, 알림받기, 상태 제보 API")
@RequestMapping("/courses")
@RequiredArgsConstructor
@RestController
public class CourseController {
    private final CourseService courseService;
    private final CourseReportCommandService courseReportCommandService;

    @Operation(
            summary = "코스 목록 조회",
            description = "등록된 국토종주 코스 목록과 각 코스의 현재 상태를 조회합니다. 인증 없이 호출할 수 있습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "코스 목록 조회 성공")
    })
    @GetMapping()
    public ResponseEntity<ApiResponse<List<CourseResponse>>> getCourse() {
        return ApiResponse.success("코스 목록 조회 성공", courseService.getCourses());
    }

    @Operation(
            summary = "내 코스 알림받기 목록 조회",
            description = "현재 로그인한 사용자가 알림받기 등록한 코스 목록을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "코스 알림받기 목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @GetMapping("/subscriptions")
    public ResponseEntity<ApiResponse<List<CourseResponse>>> getSubscriptions(@Parameter(hidden = true) @CurrentUser AuthUser authUser) {
        return ApiResponse.success("코스 알림받기 목록 조회 성공", courseService.getSubscribedCourses(authUser.userId()));
    }

    @Operation(
            summary = "코스 알림받기 등록",
            description = "현재 로그인한 사용자가 특정 코스를 알림받기 목록에 등록합니다. 이미 등록된 코스면 새로 저장하지 않고 200 응답을 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "새 알림받기 등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이미 알림받기 등록된 코스"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "코스 또는 사용자를 찾을 수 없음")
    })
    @PostMapping("/{courseId}/subscription")
    public ResponseEntity<ApiResponse<Void>> addSubscription(
            @Parameter(description = "알림받기 등록할 코스 ID", example = "1", required = true)
            @PathVariable Integer courseId,
            @Parameter(hidden = true) @CurrentUser AuthUser authUser) {
        boolean created = courseService.registerCourseSubscription(courseId, authUser.userId());

        if (created) {
            return ApiResponse.create("코스 알림받기 등록 성공", null);
        }

        return ApiResponse.success("이미 알림받기 등록된 코스입니다.", null);
    }

    @Operation(
            summary = "코스 알림받기 취소",
            description = "현재 로그인한 사용자의 특정 코스 알림받기 등록을 취소합니다. 이미 취소된 상태여도 멱등하게 200 응답을 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "알림받기 취소 성공 또는 이미 취소된 상태"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "코스 또는 사용자를 찾을 수 없음")
    })
    @DeleteMapping("/{courseId}/subscription")
    public ResponseEntity<ApiResponse<Void>> deleteSubscription(
            @Parameter(description = "알림받기 취소할 코스 ID", example = "1", required = true)
            @PathVariable Integer courseId,
            @Parameter(hidden = true) @CurrentUser AuthUser authUser) {
        boolean deleted = courseService.deleteCourseSubscription(courseId, authUser.userId());

        if (deleted) {
            return ApiResponse.success("코스 알림받기 취소 성공", null);
        }

        return ApiResponse.success("이미 알림받기 취소된 코스입니다.", null);
    }

    @Operation(
            summary = "코스 상태 제보 등록",
            description = """
                    특정 코스의 현재 상태를 제보합니다.
                    제보 등록 시 CourseReport는 ACTIVE 상태로 저장되고, Course currentStatus가 제보 타입으로 갱신됩니다.
                    동기 처리 실험 버전에서는 같은 요청 트랜잭션 안에서 해당 코스 구독자 알림을 즉시 생성합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "코스 상태 제보 등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 본문"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "코스 또는 사용자를 찾을 수 없음")
    })
    @PostMapping(value = "/{courseId}/reports", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> report(
            @Parameter(description = "상태를 제보할 코스 ID", example = "1", required = true)
            @PathVariable Integer courseId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "코스 상태 제보 요청 본문",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ReportRegisterRequest.class))
            )
            @Valid @RequestBody ReportRegisterRequest reportRegisterRequest,
            @Parameter(hidden = true) @CurrentUser AuthUser authUser) {
        courseReportCommandService.registerReport(courseId, reportRegisterRequest, authUser.userId());

        return ApiResponse.create("코스 상태 제보 등록 성공", null);
    }
}
