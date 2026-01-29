---
name: test-patterns
description: Testing patterns and conventions for Porcana. Use when writing tests.
disable-model-invocation: false
---

# Porcana Testing Patterns

## Test Structure

### Controller Test Pattern

```java
@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Test
    @DisplayName("회원가입 성공")
    void signup_Success() throws Exception {
        // Given
        SignupRequest request = new SignupRequest(
            "test@example.com",
            "password123",
            "testuser"
        );

        AuthResponse response = AuthResponse.builder()
            .accessToken("access-token")
            .refreshToken("refresh-token")
            .build();

        given(authService.signup(any(SignupCommand.class)))
            .willReturn(response);

        // When & Then
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("access-token"))
            .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 형식 오류")
    void signup_InvalidEmail() throws Exception {
        // Given
        SignupRequest request = new SignupRequest(
            "invalid-email",
            "password123",
            "testuser"
        );

        // When & Then
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
}
```

### Service Test Pattern

```java
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("회원가입 성공")
    void signup_Success() {
        // Given
        SignupCommand command = SignupCommand.builder()
            .email("test@example.com")
            .password("password123")
            .nickname("testuser")
            .provider(AuthProvider.EMAIL)
            .build();

        User savedUser = User.builder()
            .id(UUID.randomUUID())
            .email("test@example.com")
            .nickname("testuser")
            .build();

        given(userRepository.existsByEmail(command.getEmail()))
            .willReturn(false);
        given(passwordEncoder.encode(command.getPassword()))
            .willReturn("encoded-password");
        given(userRepository.save(any(User.class)))
            .willReturn(savedUser);

        // When
        AuthResponse response = authService.signup(command);

        // Then
        assertThat(response.getAccessToken()).isNotNull();
        assertThat(response.getRefreshToken()).isNotNull();
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    void signup_DuplicateEmail() {
        // Given
        SignupCommand command = SignupCommand.builder()
            .email("duplicate@example.com")
            .password("password123")
            .nickname("testuser")
            .provider(AuthProvider.EMAIL)
            .build();

        given(userRepository.existsByEmail(command.getEmail()))
            .willReturn(true);

        // When & Then
        assertThatThrownBy(() -> authService.signup(command))
            .isInstanceOf(DuplicateEmailException.class);
    }
}
```

### Repository Test Pattern

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AssetRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("porcana_test");

    @Autowired
    private AssetRepository assetRepository;

    @Test
    @DisplayName("섹터로 활성 자산 조회")
    void findBySectorInAndActiveTrue() {
        // Given
        Asset asset1 = Asset.builder()
            .market(Market.US)
            .symbol("AAPL")
            .name("Apple Inc.")
            .type(AssetType.STOCK)
            .sector(Sector.INFORMATION_TECHNOLOGY)
            .active(true)
            .build();

        Asset asset2 = Asset.builder()
            .market(Market.US)
            .symbol("MSFT")
            .name("Microsoft Corp.")
            .type(AssetType.STOCK)
            .sector(Sector.INFORMATION_TECHNOLOGY)
            .active(true)
            .build();

        assetRepository.saveAll(List.of(asset1, asset2));

        // When
        List<Asset> results = assetRepository.findBySectorInAndActiveTrue(
            Set.of(Sector.INFORMATION_TECHNOLOGY)
        );

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).extracting(Asset::getSymbol)
            .containsExactlyInAnyOrder("AAPL", "MSFT");
    }
}
```

### Batch Job Test Pattern

```java
@SpringBatchTest
@SpringBootTest
class KrDailyPriceJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AssetPriceRepository assetPriceRepository;

    @Test
    @DisplayName("한국 주식 일일 가격 업데이트")
    void krDailyPriceJob_Success() throws Exception {
        // Given
        Asset asset = Asset.builder()
            .market(Market.KR)
            .symbol("005930")
            .name("삼성전자")
            .type(AssetType.STOCK)
            .active(true)
            .build();
        assetRepository.save(asset);

        // When
        JobExecution jobExecution = jobLauncherTestUtils.launchJob();

        // Then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<AssetPrice> prices = assetPriceRepository.findByAssetId(asset.getId());
        assertThat(prices).isNotEmpty();
    }
}
```

## Test Conventions

### Naming
- **클래스명**: `{ClassName}Test`
- **메서드명**: `{methodName}_{scenario}`
- **DisplayName**: 한글로 명확하게 작성

### Given-When-Then
```java
@Test
void testMethod() {
    // Given - 테스트 준비
    // When - 테스트 실행
    // Then - 결과 검증
}
```

### Mocking Strategy
- **Controller**: `@WebMvcTest` + `@MockBean` for services
- **Service**: `@ExtendWith(MockitoExtension.class)` + `@Mock`
- **Repository**: `@DataJpaTest` + Testcontainers

### Assertions
- **AssertJ** 사용: `assertThat(actual).isEqualTo(expected)`
- **JsonPath** (Controller): `.andExpect(jsonPath("$.field").value("value"))`

### Test Data Builders
```java
public class AssetFixture {
    public static Asset createApple() {
        return Asset.builder()
            .market(Market.US)
            .symbol("AAPL")
            .name("Apple Inc.")
            .type(AssetType.STOCK)
            .sector(Sector.INFORMATION_TECHNOLOGY)
            .currentRiskLevel(4)
            .active(true)
            .build();
    }

    public static Asset createSpy() {
        return Asset.builder()
            .market(Market.US)
            .symbol("SPY")
            .name("SPDR S&P 500 ETF")
            .type(AssetType.ETF)
            .assetClass(AssetClass.EQUITY_INDEX)
            .currentRiskLevel(2)
            .active(true)
            .build();
    }
}
```

### Security Test Config
```java
@TestConfiguration
public class TestSecurityConfig {
    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
```

## Coverage Goals

- **Controller**: 90%+
- **Service**: 95%+
- **Repository**: 80%+
- **Batch Jobs**: 85%+

## Integration Test Tips

1. **Testcontainers** 사용 (PostgreSQL)
2. **트랜잭션 격리** (`@Transactional`)
3. **테스트 순서 독립성** 보장
4. **외부 API Mocking** (WireMock, MockRestServiceServer)