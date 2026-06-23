package kr.kakaotech.community.auth;

import java.util.UUID;

public record AuthUser(UUID userId, String role) {
}
