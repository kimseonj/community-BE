package kr.kakaotech.community.auth;

import kr.kakaotech.community.exception.CustomException;
import kr.kakaotech.community.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserArgumentResolverTest {

    private final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver();

    @Test
    @DisplayName("CurrentUser AuthUser 파라미터를 지원한다")
    void supportsCurrentUserAuthUserParameter() throws Exception {
        MethodParameter parameter = parameter("requiredUser");

        assertThat(resolver.supportsParameter(parameter)).isTrue();
    }

    @Test
    @DisplayName("CurrentUser 어노테이션이 없으면 지원하지 않는다")
    void doesNotSupportParameterWithoutCurrentUserAnnotation() throws Exception {
        MethodParameter parameter = parameter("plainAuthUser");

        assertThat(resolver.supportsParameter(parameter)).isFalse();
    }

    @Test
    @DisplayName("request attribute에서 인증 사용자를 생성한다")
    void resolveArgument_success() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = authenticatedRequest(userId.toString(), "USER");

        // when
        AuthUser authUser = (AuthUser) resolver.resolveArgument(
                parameter("requiredUser"),
                null,
                new ServletWebRequest(request),
                null
        );

        // then
        assertThat(authUser.userId()).isEqualTo(userId);
        assertThat(authUser.role()).isEqualTo("USER");
    }

    @Test
    @DisplayName("필수 인증에서 userId attribute가 없으면 UNAUTHORIZED")
    void resolveArgument_missingRequiredUser() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();

        // when & then
        assertThatThrownBy(() -> resolver.resolveArgument(
                parameter("requiredUser"),
                null,
                new ServletWebRequest(request),
                null
        ))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("필수 인증에서 userId 형식이 잘못되면 UNAUTHORIZED")
    void resolveArgument_invalidUserIdFormat() throws Exception {
        // given
        MockHttpServletRequest request = authenticatedRequest("invalid-user-id", "USER");

        // when & then
        assertThatThrownBy(() -> resolver.resolveArgument(
                parameter("requiredUser"),
                null,
                new ServletWebRequest(request),
                null
        ))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("선택 인증에서 userId attribute가 없으면 null")
    void resolveArgument_optionalUserMissing() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();

        // when
        Object result = resolver.resolveArgument(
                parameter("optionalUser"),
                null,
                new ServletWebRequest(request),
                null
        );

        // then
        assertThat(result).isNull();
    }

    private MockHttpServletRequest authenticatedRequest(String userId, String role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", userId);
        request.setAttribute("role", role);
        return request;
    }

    private MethodParameter parameter(String methodName) throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod(methodName, AuthUser.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private static class TestController {
        void requiredUser(@CurrentUser AuthUser authUser) {
        }

        void optionalUser(@CurrentUser(required = false) AuthUser authUser) {
        }

        void plainAuthUser(AuthUser authUser) {
        }
    }
}
