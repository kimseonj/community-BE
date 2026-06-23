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

## 선택 근거

`@CurrentUser`와 `HandlerMethodArgumentResolver`를 선택한 이유는 인증 검증 책임과 컨트롤러 파라미터 변환 책임을 분리하면서도 기존 인증 구조를 크게 흔들지 않기 위해서다.

현재 구조에서 인증은 이미 `AuthFilter`와 `AuthenticationStrategy` 구현체가 컨트롤러 진입 전에 처리한다. 따라서 이번 이슈의 핵심은 인증 방식을 새로 만드는 것이 아니라, 필터가 남긴 인증 결과를 컨트롤러가 안전하고 일관된 타입으로 받게 만드는 것이다. ArgumentResolver는 Spring MVC가 컨트롤러 메서드 파라미터를 해석하는 표준 확장 지점이므로 이 목적에 가장 직접적으로 맞는다.

이 방식의 장점은 다음과 같다.

- 컨트롤러에서 `HttpServletRequest`와 attribute key 문자열을 제거할 수 있다.
- UUID 파싱, attribute 누락, 선택 인증 처리를 한 곳에서 관리할 수 있다.
- 서비스 계층은 JWT, 세션, request attribute 같은 웹/인증 구현 세부사항을 알 필요가 없다.
- 기존 JWT/세션 인증 필터와 API 응답 구조를 유지하므로 변경 범위가 작다.
- 인증이 필수인 API와 선택인 API를 `required` 옵션으로 명시할 수 있다.

검토한 대안은 다음과 같다.

- 공통 유틸 메서드: 중복 코드는 줄지만 컨트롤러가 여전히 `HttpServletRequest`에 의존하고, 파라미터 타입으로 인증 사용자라는 의도가 드러나지 않는다.
- 서비스에서 request attribute를 직접 읽기: 웹 계층 책임이 서비스로 넘어가므로 계층 경계가 흐려진다.
- Spring Security `SecurityContext` 전환: 장기적으로 더 표준적인 방향이지만, 현재 프로젝트는 Spring Security filter chain 대신 커스텀 `AuthFilter`를 사용하고 있어 이번 이슈 범위보다 변경량이 크다.

따라서 이번 이슈에서는 `@CurrentUser` 기반 ArgumentResolver를 적용하고, 향후 Spring Security `Authentication`/`SecurityContext` 전환은 별도 작업으로 남긴다.

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
