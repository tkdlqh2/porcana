# Claude Code - Porcana Project Guide

이 디렉토리에는 Porcana 프로젝트를 위한 Claude Code Skills와 Subagents가 포함되어 있습니다.

## 📁 구조

```
.claude/
├── skills/              # 참고 자료 및 간단한 작업
│   ├── api-conventions/     # API 설계 패턴
│   ├── batch-jobs/          # 배치 작업 레퍼런스
│   ├── arena-specs/         # Arena 알고리즘 상세
│   ├── entity-reference/    # Entity 구조 참고
│   └── test-patterns/       # 테스트 작성 패턴
│
├── agents/              # 복잡한 독립 작업
│   ├── arena-analyzer/      # Arena 로직 분석
│   ├── batch-debugger/      # Batch 작업 디버깅
│   ├── api-tester/          # API 테스트 실행
│   ├── security-checker/    # 보안 취약점 스캔
│   └── performance-analyzer/ # 성능 분석
│
└── README.md            # 이 파일
```

---

## 🎯 Skills (참고 자료)

Skills는 대화 중 자동으로 로드되거나 `/skill-name` 형태로 직접 호출할 수 있습니다.

### 1. api-conventions
**설명**: Porcana API 설계 패턴 (Request DTO, Command Pattern, Swagger Auth)

**사용 시기**:
- 새로운 API 엔드포인트 작성할 때
- Request/Response DTO 구조 참고할 때
- Command Pattern 구현할 때

**호출 방법**:
```
Claude가 API 관련 작업 시 자동으로 참조합니다.
```

**주요 내용**:
- Request DTO는 Java Record로 작성
- Response/Command는 @Builder + Lombok
- Controller → Command.from(request) → Service 패턴
- @SecurityRequirement로 인증 표시

---

### 2. batch-jobs
**설명**: Spring Batch 작업 레퍼런스 및 실행 방법

**사용 시기**:
- 배치 작업 실행 명령어 확인할 때
- 배치 스케줄 확인할 때
- 새로운 배치 작업 만들 때

**호출 방법**:
```
배치 작업 관련 질문 시 자동으로 참조합니다.
```

**주요 내용**:
- 주간/일일 배치 스케줄 정보
- 각 배치 Job 실행 명령어
- Rate Limiting 설정
- 위험도 계산 로직

---

### 3. arena-specs
**설명**: Arena 자산 추천 알고리즘 상세 스펙

**사용 시기**:
- Arena 로직 구현/수정할 때
- Weight 계산 로직 참고할 때
- Diversity 조건 확인할 때

**호출 방법**:
```
Arena 관련 작업 시 자동으로 참조합니다.
```

**주요 내용**:
- Round 구조 (Round 0: Pre Round, Round 1-10: Asset)
- Weight Calculation (Risk, Sector, Type, Diversity)
- Bucket Sampling 전략
- Fallback 전략

---

### 4. entity-reference
**설명**: Entity 구조 및 관계 참고 자료

**사용 시기**:
- Entity 구조 확인할 때
- 테이블 관계 파악할 때
- 새로운 Entity 추가할 때

**호출 방법**:
```
Entity 관련 작업 시 자동으로 참조합니다.
```

**주요 내용**:
- Core Entities (Asset, Portfolio, User, ArenaSession 등)
- Enum 정의 (Market, Sector, AssetClass 등)
- 관계 및 제약 조건
- XOR 소유권 패턴

---

### 5. test-patterns
**설명**: 테스트 작성 패턴 및 컨벤션

**사용 시기**:
- 테스트 코드 작성할 때
- Mock 설정 방법 참고할 때
- Test Fixture 만들 때

**호출 방법**:
```
테스트 작성 시 자동으로 참조합니다.
```

**주요 내용**:
- Controller/Service/Repository 테스트 패턴
- Given-When-Then 구조
- Mocking 전략
- Test Data Builders

---

## 🤖 Subagents (복잡한 작업)

Subagents는 독립적인 컨텍스트에서 실행되며, 복잡한 분석/디버깅 작업에 사용됩니다.

### 1. arena-analyzer
**설명**: Arena 추천 알고리즘 정확성 분석

**사용 시기**:
- Arena 로직 변경 후 검증할 때
- Weight 계산 오류 의심될 때
- Diversity 조건 검증할 때

**호출 방법**:
```
"Use the arena-analyzer subagent to review the recommendation logic"
```

**분석 내용**:
- Weight 계산 정확성
- Diversity 조건 검증
- Bucket Sampling 효율성
- Edge Case 처리

**출력 예시**:
```
✅ Correct Implementations:
- ETF typeWeight correctly set to 2.5x

⚠️ Potential Issues:
- riskWeight() for BALANCED profile edge case

🔴 Critical Problems:
- NONE FOUND

💡 Optimization Suggestions:
- Cache preferredCandidates across retries

🧪 Recommended Tests:
- Test case: riskWeight with null riskLevel
```

---

### 2. batch-debugger
**설명**: Spring Batch 작업 디버깅 전문가

**사용 시기**:
- 배치 작업 실패할 때
- 데이터가 제대로 입력 안 될 때
- API 연동 오류 발생할 때

**호출 방법**:
```
"Use the batch-debugger subagent to investigate the krDailyPriceJob failure"
```

**디버깅 과정**:
1. 에러 상세 캡처
2. Job Configuration 분석
3. 데이터 소스 검증
4. Step 로직 리뷰
5. Fix 구현 및 검증

**출력 예시**:
```
🔍 Issue Summary:
Job: krDailyPriceJob
Error: NullPointerException in AssetPriceWriter

🧪 Root Cause Analysis:
API response returns null for 'volume' on holidays

🛠️ Implemented Fix:
Added null check and default value

✅ Verification:
Re-ran job: SUCCESS, 348 records inserted

📋 Prevention Recommendations:
Add integration test with null volume scenario
```

---

### 3. api-tester
**설명**: API 엔드포인트 테스트 및 검증

**사용 시기**:
- 새로운 API 구현 후 테스트할 때
- API 이슈 조사할 때
- 통합 테스트 전 검증할 때

**호출 방법**:
```
"Use the api-tester subagent to test the auth endpoints"
```

**테스트 항목**:
- Happy Path 테스트
- Validation 에러 테스트
- 인증/인가 테스트
- Edge Case 테스트
- Guest Session 테스트

**출력 예시**:
```
📋 Test Summary:
Tested: 12 endpoints
Passed: 45/48 test cases (93.75%)

✅ Passed Tests:
POST /api/v1/auth/signup
  ✓ Valid signup returns 201
  ✓ Invalid email returns 400

❌ Failed Tests:
GET /api/v1/portfolios/{id}
  ✗ Non-existent portfolio returns 500 (expected 404)

🔧 Issues Found:
- Missing UUID format validation

📊 Response Time Analysis:
POST /api/v1/auth/signup: 127ms ✅
```

---

### 4. security-checker
**설명**: 보안 취약점 스캐너 (OWASP Top 10)

**사용 시기**:
- 코드 변경 후 보안 검증할 때
- 릴리즈 전 보안 체크할 때
- 취약점 조사할 때

**호출 방법**:
```
"Use the security-checker subagent to scan for vulnerabilities"
```

**검사 항목**:
- Authentication & Authorization
- Input Validation
- Data Exposure (hardcoded secrets)
- API Security (rate limiting)
- SQL Injection
- OWASP Top 10

**출력 예시**:
```
🔴 Critical Vulnerabilities:

[CRIT-001] Hardcoded API Key in application.yml
File: src/main/resources/application.yml:23
Fix: Use environment variables

[CRIT-002] SQL Injection Risk in AssetRepository
Fix: Use parameterized query

🟠 High Risk Issues:

[HIGH-001] Missing Rate Limiting on Auth Endpoints

✅ Security Strengths:
- Password hashing with BCrypt ✓
- JWT token validation ✓

📊 Summary:
Critical: 2, High: 2, Medium: 2, Low: 2
```

---

### 5. performance-analyzer
**설명**: 성능 분석 및 최적화 전문가

**사용 시기**:
- 느린 엔드포인트 조사할 때
- 배치 작업 최적화할 때
- N+1 쿼리 문제 찾을 때
- 인덱스 누락 확인할 때

**호출 방법**:
```
"Use the performance-analyzer subagent to profile the application"
```

**분석 항목**:
- N+1 Query 문제
- Missing Indexes
- EAGER vs LAZY Fetch
- Over-fetching Data
- Batch Job Chunk Size
- Caching 기회

**출력 예시**:
```
🐢 Performance Bottlenecks:

[PERF-001] GET /api/v1/portfolios - 2.4s (CRITICAL)
Issue: N+1 query problem
Fix: Use @EntityGraph
Expected: 2.4s → 150ms (16x faster)

🔍 Query Analysis:

[QUERY-001] Missing index on assets(sector, active)
Fix: CREATE INDEX idx_asset_sector_active

💾 Memory Issues:

[MEM-001] Arena session holds all shown assets

⚡ Optimization Recommendations:
1. Add caching for active assets
2. Implement pagination
3. Optimize batch chunk size

📊 Performance Metrics:
GET /api/v1/portfolios: 2400ms → 150ms (16x faster)
```

---

## 🚀 사용 예시

### Skill 사용 예시

```
사용자: "새로운 API 엔드포인트 만들어줘"
Claude: (api-conventions skill 자동 참조)
        "Request DTO는 Java Record로 작성합니다..."
```

### Subagent 사용 예시

```
사용자: "Arena 로직 검증해줘"
Claude: "arena-analyzer subagent를 사용해서 분석하겠습니다."
        (독립적 컨텍스트에서 실행)
        (결과 요약 반환)
```

```
사용자: "배치 작업이 실패했어"
Claude: "batch-debugger subagent로 조사하겠습니다."
        (에러 캡처 → 원인 분석 → Fix 제안)
```

```
사용자: "API 테스트해줘"
Claude: "api-tester subagent로 엔드포인트를 테스트하겠습니다."
        (curl 실행 → 응답 검증 → 리포트 생성)
```

---

## 💡 팁

### Skills vs Subagent 선택

**Skills 사용**:
- 빠른 참고가 필요할 때
- 대화에 정보가 남아야 할 때
- 간단한 패턴/예시 확인할 때

**Subagent 사용**:
- 복잡한 분석/디버깅 필요할 때
- 독립적인 조사가 필요할 때
- 출력이 매우 길 때 (요약만 필요)

### 병렬 실행

여러 Subagent를 동시에 실행 가능:
```
"Use arena-analyzer and security-checker subagents in parallel"
```

### 백그라운드 실행

긴 작업을 백그라운드로 실행:
```
"Use the api-tester subagent to run full test suite in the background"
```

---

## 📚 추가 리소스

- **CLAUDE.md**: 전체 프로젝트 스펙 및 API 계약
- **Claude Code 공식 문서**: https://github.com/anthropics/claude-code

---

이제 Claude Code가 Porcana 프로젝트에 최적화된 도구들을 사용할 수 있습니다! 🎉