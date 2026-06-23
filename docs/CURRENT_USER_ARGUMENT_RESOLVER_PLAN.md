# 인증 사용자 ID 추출 공통화 계획

## 문제 정의

현재 인증 필터는 컨트롤러 진입 전에 JWT 또는 세션을 검증하고 `HttpServletRequest` attribute에 `userId`, `role`을 저장한다. 하지만 여러 컨트롤러가 이 값을 직접 꺼내 `toString()`, `UUID.fromString(...)`을 반복한다.

이 방식은 attribute 누락 시 NPE가 발생하고, UUID 파싱 실패가 일관된 인증 예외로 정리되지 않으며, 인증 사용자 전달 정책을 바꾸려면 컨트롤러마다 수정해야 한다.

## 설계 방향

- `@CurrentUser` 파라미터 어노테이션과 `CurrentUserArgumentResolver`를 추가한다.
- resolver는 request attribute의 `userId`, `role`을 읽어 `AuthUser` record로 변환한다.
- 필수 인증 API는 `@CurrentUser AuthUser authUser`를 사용한다.
- 인증이 선택인 좋아요 상태 조회 API는 `@CurrentUser(required = false) AuthUser authUser`를 사용한다.
- attribute가 없거나 UUID 형식이 잘못된 경우 필수 인증 API에서는 `ErrorCode.UNAUTHORIZED`로 일관 처리한다.
- 기존 `AuthFilter`, `JwtFilter`, `SessionFilter`의 인증 검증 책임은 유지한다.

## 구현 범위

- 새 타입 추가
  - `CurrentUser`
  - `AuthUser`
  - `CurrentUserArgumentResolver`
- `WebConfig`에 argument resolver를 등록한다.
- 컨트롤러의 `request.getAttribute("userId")` 직접 호출을 제거한다.
- `LikeService.getLikeStatus`는 컨트롤러에서 이미 파싱된 `Optional<UUID>`를 받도록 변경한다.
- `AuthController`, `UserController`의 로그아웃/토큰 갱신처럼 쿠키 삭제나 요청 URI 확인 때문에 `HttpServletRequest` 자체가 필요한 코드는 유지한다.

## 검증 계획

- `CurrentUserArgumentResolverTest`
  - 정상 `userId`, `role`이면 `AuthUser` 생성
  - 필수 인증에서 attribute 누락 시 `UNAUTHORIZED`
  - 필수 인증에서 UUID 형식 오류 시 `UNAUTHORIZED`
  - 선택 인증에서 attribute 누락 시 `null`
- 컨트롤러/통합 테스트
  - 기존 인증 필요 API 기능 테스트 통과
  - 좋아요 상태 조회는 비로그인 요청에서도 기존처럼 false 반환
- 전체 검증
  - `./gradlew test`
  - `rg -n 'getAttribute\\("userId"\\)' src/main/java/kr/kakaotech/community/controller` 결과가 없어야 한다.

## 후속 작업

- 필요하면 다음 단계에서 Spring Security `Authentication`/`SecurityContext` 기반으로 전환한다.
- 이번 이슈에서는 인증 필터의 token/session 검증 방식과 API 응답 포맷은 바꾸지 않는다.
