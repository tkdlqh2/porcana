# Admin API Implementation Plan

## Overview
Admin API 개발: 관리자 계정 관리, 회원 조회/관리, 종목 이미지/배당 관리, 포트폴리오 조회

## User Decisions
- **Admin 구조**: User 테이블에 role 필드 추가 (UserRole: USER, ADMIN)
- **초기 Admin**: 환경변수 기반 자동 생성 (ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NICKNAME)
- **인증 방식**: 기존 JWT + role claim, @PreAuthorize로 권한 체크

---

## Phase 1: Database & Entity (Foundation)

### 1.1 Migration: V26__add_user_role.sql
```sql
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD CONSTRAINT chk_user_role CHECK (role IN ('USER', 'ADMIN'));
CREATE INDEX idx_users_role ON users(role);
```

### 1.2 Create: UserRole.java
**Path**: `src/main/java/com/porcana/domain/user/entity/UserRole.java`
```java
public enum UserRole { USER, ADMIN }
```

### 1.3 Modify: User.java
**Path**: `src/main/java/com/porcana/domain/user/entity/User.java`
- Add `role` field with `@Enumerated(EnumType.STRING)`
- Default value: `UserRole.USER`
- Add `isAdmin()` method

### 1.4 Modify: UserRepository.java
**Path**: `src/main/java/com/porcana/domain/user/repository/UserRepository.java`
- Add: `Page<User> findByDeletedAtIsNull(Pageable pageable)`
- Add: `Page<User> findByRoleAndDeletedAtIsNull(UserRole role, Pageable pageable)`
- Add: `boolean existsByRoleAndDeletedAtIsNull(UserRole role)`
- Add: search methods with email/nickname containing

---

## Phase 2: Security Layer (JWT + Role)

### 2.1 Modify: JwtTokenProvider.java
**Path**: `src/main/java/com/porcana/global/security/JwtTokenProvider.java`
- Change: `createAccessToken(UUID userId)` → `createAccessToken(UUID userId, String role)`
- Add role claim: `.claim("role", role)`
- Add: `getRoleFromToken(String token)` method

### 2.2 Modify: JwtAuthenticationFilter.java
**Path**: `src/main/java/com/porcana/global/security/JwtAuthenticationFilter.java`
- Extract role from token
- Create `SimpleGrantedAuthority("ROLE_" + role)`
- Set authorities in authentication

### 2.3 Modify: SecurityConfig.java
**Path**: `src/main/java/com/porcana/global/config/SecurityConfig.java`
- Add: `@EnableMethodSecurity`
- Add: `.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")`

### 2.4 Modify: AuthService.java
**Path**: `src/main/java/com/porcana/domain/auth/service/AuthService.java`
- Update all `createAccessToken()` calls to pass role
- Lines: 52, 71, 176

---

## Phase 3: Admin Initialization

### 3.1 Create: AdminInitializer.java
**Path**: `src/main/java/com/porcana/global/config/AdminInitializer.java`
- `@Component` with `ApplicationRunner`
- Read env vars: `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_NICKNAME`
- Check if admin exists, create if not

### 3.2 Modify: application.yml
- Add admin config section (optional, can use @Value directly)

---

## Phase 4: Admin API - DTOs

### Request DTOs (Records)
**Path**: `src/main/java/com/porcana/domain/admin/dto/request/`

| File | Fields |
|------|--------|
| `CreateAdminRequest.java` | email, password, nickname |
| `UpdateAssetImageRequest.java` | imageUrl |
| `UpdateAssetDividendRequest.java` | dividendAvailable, dividendYield, dividendFrequency, dividendCategory, dividendDataStatus, lastDividendDate |

### Response DTOs (Builder)
**Path**: `src/main/java/com/porcana/domain/admin/dto/response/`

| File | Purpose |
|------|---------|
| `AdminUserListResponse.java` | Paginated user list |
| `AdminUserDetailResponse.java` | User detail with stats |
| `AdminPortfolioListResponse.java` | Paginated portfolio list |
| `AdminPortfolioDetailResponse.java` | Portfolio detail with owner info |

---

## Phase 5: Admin API - Service & Controller

### 5.1 Create: AdminService.java
**Path**: `src/main/java/com/porcana/domain/admin/service/AdminService.java`

Methods:
- `createAdmin(CreateAdminRequest)` - 새 관리자 생성
- `getUsers(Pageable, filters)` - 회원 목록 조회
- `getUserDetail(UUID)` - 회원 상세 조회
- `deleteUser(UUID)` - 회원 탈퇴 처리 (soft delete)
- `updateAssetImage(UUID, String)` - 종목 이미지 수정
- `updateAssetDividend(UUID, UpdateAssetDividendRequest)` - 배당 정보 수정
- `getPortfolios(Pageable, filters)` - 포트폴리오 목록 조회
- `getPortfolioDetail(UUID)` - 포트폴리오 상세 조회 (소유권 체크 없음)

### 5.2 Create: AdminController.java
**Path**: `src/main/java/com/porcana/domain/admin/AdminController.java`

```java
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "JWT")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "관리자 API")
public class AdminController { ... }
```

---

## API Endpoints

### Admin Management
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/admin/admins` | 새 관리자 생성 |

### User Management
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/admin/users` | 회원 목록 (pagination, search) |
| GET | `/api/v1/admin/users/{userId}` | 회원 상세 |
| DELETE | `/api/v1/admin/users/{userId}` | 회원 탈퇴 처리 |

### Asset Management
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/admin/assets` | 종목 목록 (filtering) |
| PATCH | `/api/v1/admin/assets/{assetId}/image` | 이미지 URL 수정 |
| PATCH | `/api/v1/admin/assets/{assetId}/dividend` | 배당 정보 수정 |

### Portfolio Management
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/admin/portfolios` | 전체 포트폴리오 목록 |
| GET | `/api/v1/admin/portfolios/{portfolioId}` | 포트폴리오 상세 |

---

## Implementation Order

```
1. V26__add_user_role.sql
2. UserRole.java
3. User.java (modify)
4. UserRepository.java (add methods)
5. JwtTokenProvider.java (add role claim)
6. JwtAuthenticationFilter.java (extract role)
7. SecurityConfig.java (@EnableMethodSecurity, admin endpoint)
8. AuthService.java (pass role to token)
9. AdminInitializer.java
10. Admin DTOs (request/response)
11. AdminService.java
12. AdminController.java
```

---

## Critical Files Summary

| File | Action |
|------|--------|
| `src/main/resources/db/migration/V26__add_user_role.sql` | CREATE |
| `src/main/java/com/porcana/domain/user/entity/UserRole.java` | CREATE |
| `src/main/java/com/porcana/domain/user/entity/User.java` | MODIFY |
| `src/main/java/com/porcana/domain/user/repository/UserRepository.java` | MODIFY |
| `src/main/java/com/porcana/global/security/JwtTokenProvider.java` | MODIFY |
| `src/main/java/com/porcana/global/security/JwtAuthenticationFilter.java` | MODIFY |
| `src/main/java/com/porcana/global/config/SecurityConfig.java` | MODIFY |
| `src/main/java/com/porcana/domain/auth/service/AuthService.java` | MODIFY |
| `src/main/java/com/porcana/global/config/AdminInitializer.java` | CREATE |
| `src/main/java/com/porcana/domain/admin/**` | CREATE (all) |

---

## Environment Variables

Admin 초기화에 필요한 환경변수:
```
ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=SecurePassword123!
ADMIN_NICKNAME=Admin
```

---

## Security Considerations

1. **Role Validation**: 기존 ADMIN만 새 ADMIN 생성 가능
2. **Self-Delete Prevention**: Admin은 자기 자신 삭제 불가
3. **Password Requirements**: Admin 계정에 강력한 비밀번호 요구
4. **Audit Logging**: 모든 Admin 작업 로깅 고려
