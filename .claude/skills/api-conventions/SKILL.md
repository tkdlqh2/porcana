---
name: api-conventions
description: Porcana API design patterns and conventions. Use when creating new endpoints or DTOs.
disable-model-invocation: false
---

# Porcana API Design Conventions

## Request DTO as Record

**Request DTO**: Java Record로 작성 (불변성, 간결함)

```java
public record SignupRequest(
    @Email(message = "올바른 이메일 형식이 아닙니다")
    String email,

    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다")
    String password,

    @NotBlank(message = "닉네임은 필수입니다")
    String nickname
) {}
```

**이유:**
- Record는 불변 객체로 API 요청 데이터에 적합
- Validation annotation을 필드에 직접 적용 가능
- getter 메서드 자동 생성 (field명과 동일: `request.email()`)

## Response/Command DTO

**Response DTO**: @Builder + Lombok 사용 (유연한 생성)
**Command DTO**: @Builder + Lombok 사용 (내부 로직용)

```java
@Builder
@Getter
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private UserInfo user;
}
```

## Command Pattern Flow

```
Request DTO (SignupRequest)
    ↓ Command.from(request)
Command DTO (SignupCommand)
    ↓ Service로 전달
Entity (User.from(command))
```

**Controller Example:**
```java
@PostMapping("/signup")
public ResponseEntity<AuthResponse> signup(@RequestBody SignupRequest request) {
    SignupCommand command = SignupCommand.from(request);
    AuthResponse response = authService.signup(command);
    return ResponseEntity.ok(response);
}
```

**Command Static Factory:**
```java
public static SignupCommand from(SignupRequest request) {
    return SignupCommand.builder()
        .email(request.email())
        .password(request.password())
        .nickname(request.nickname())
        .provider(User.AuthProvider.EMAIL)
        .build();
}
```

## Swagger Authentication

**인증 필요 API:**
```java
@SecurityRequirement(name = "JWT")
@RestController
@RequestMapping("/api/v1")
public class UserController {
    // JWT 필요, Swagger UI에서 자물쇠 표시
}
```

**공개 API:**
```java
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    // @SecurityRequirement 없음 = 공개 API
}
```

**@CurrentUser Annotation:**
```java
@GetMapping("/me")
public ResponseEntity<UserResponse> getMe(@CurrentUser UUID userId) {
    // JWT 토큰에서 userId 자동 추출
}
```

## Base Configuration

- Base Path: `/api/v1`
- Auth: `Authorization: Bearer {accessToken}`
- Content-Type: `application/json`
- Date format: ISO-8601 (YYYY-MM-DD)

## Error Handling

- 일관된 에러 포맷
- Validation 메시지 포함
- 적절한 HTTP status code 반환