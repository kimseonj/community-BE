package kr.kakaotech.community.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    /**
     * USER 에러
     */
    DUPLICATED_EMAIL("이미 사용중인 이메일입니다.", HttpStatus.CONFLICT),
    DUPLICATED_NICKNAME("이미 사용중인 닉네임입니다.", HttpStatus.CONFLICT),

    NOT_FOUND_USER("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    BAD_PASSWORD("사용중인 비밀번호를 확인해주세요.", HttpStatus.BAD_REQUEST),

    /**
     * Post 에러
     */
    NOT_FOUND_POST("존재하지 않는 게시글입니다.", HttpStatus.NOT_FOUND),
    BAD_REQUEST_FILTER("알맞지 않은 조건입니다.", HttpStatus.BAD_REQUEST),

    /**
     * Course 에러
     */
    NOT_FOUND_COURSE("존재하지 않는 코스입니다.", HttpStatus.NOT_FOUND),

    /**
     * Notification 에러
     */
    NOT_FOUND_NOTIFICATION("존재하지 않는 알림입니다.", HttpStatus.NOT_FOUND),

    /**
     * Comment 에러
     */
    NOT_FOUND_COMMENT("존재하지 않는 댓글입니다.", HttpStatus.NOT_FOUND),
    BAD_REQUEST_COMMENT("이미 삭제된 댓글입니다.", HttpStatus.BAD_REQUEST),

    /**
     * Auth 에러
     */
    INVALID_LOGIN_JSON("적절하지 않은 로그인 요청입니다. login email, password 요청을 보내주세요.", HttpStatus.BAD_REQUEST),
    LOGIN_FAIL("로그인 실패", HttpStatus.UNAUTHORIZED),

    /**
     * JWT
     */
    EXPIRED_JWT("JWT 기간 만료되었습니다.", HttpStatus.UNAUTHORIZED),
    EXPIRED_ACCESS_TOKEN("expiredAccessToken", HttpStatus.UNAUTHORIZED),
    NON_SIGNATURE_JWT("올바르지않은 서명입니다.", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN("invalidToken", HttpStatus.UNAUTHORIZED),

    /**
     * Session
     */
    INVALID_SESSION("세션이 존재하지 않습니다.", HttpStatus.UNAUTHORIZED),

    /**
     * 이미지 에러
     */
    EMPTY_IMAGE("이미지가 존재하지 않습니다.", HttpStatus.BAD_REQUEST),
    NOT_FOUND_IMAGE("이미지를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    IMAGE_BAD_CONTENT_TYPE("이미지 타입 에러", HttpStatus.BAD_REQUEST),
    IMAGE_TOO_LARGE("이미지 용량은 5MB 이하로 등록해주세요.", HttpStatus.BAD_REQUEST),
    IMAGE_TOO_MANY("등록 가능한 이미지수는 5장이 최대입니다.", HttpStatus.BAD_REQUEST),

    /**
     * 권한 에러
     */
    FORBIDDEN("권한이 없습니다.", HttpStatus.FORBIDDEN),
    UNAUTHORIZED("접근할 수 없습니다.", HttpStatus.UNAUTHORIZED),
    BAD_REQUEST("잘못된 요청입니다.", HttpStatus.BAD_REQUEST),

    /**
     * ERROR
     */
    SERVER_ERROR("INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String message;
    private final HttpStatus status;

    ErrorCode(String message, HttpStatus status) {
        this.message = message;
        this.status = status;
    }
}
