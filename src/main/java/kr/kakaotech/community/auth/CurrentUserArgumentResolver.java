package kr.kakaotech.community.auth;

import jakarta.servlet.http.HttpServletRequest;
import kr.kakaotech.community.exception.CustomException;
import kr.kakaotech.community.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String USER_ID_ATTRIBUTE = "userId";
    private static final String ROLE_ATTRIBUTE = "role";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && AuthUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        CurrentUser currentUser = parameter.getParameterAnnotation(CurrentUser.class);
        boolean required = currentUser == null || currentUser.required();

        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            return resolveMissingUser(required);
        }

        Object userIdAttribute = request.getAttribute(USER_ID_ATTRIBUTE);
        Object roleAttribute = request.getAttribute(ROLE_ATTRIBUTE);
        if (userIdAttribute == null) {
            return resolveMissingUser(required);
        }
        if (roleAttribute == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        try {
            return new AuthUser(UUID.fromString(userIdAttribute.toString()), roleAttribute.toString());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
    }

    private Object resolveMissingUser(boolean required) {
        if (!required) {
            return null;
        }
        throw new CustomException(ErrorCode.UNAUTHORIZED);
    }
}
