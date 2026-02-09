# Discord Notification Setup Guide

Discord Webhook을 통한 배치 작업 모니터링 가이드입니다.

## 1. Discord Webhook URL 생성

### Step 1: Discord 채널에서 Webhook 생성
1. Discord 서버의 채널 설정으로 이동
2. **통합(Integrations)** > **웹훅(Webhooks)** 클릭
3. **새 웹훅(New Webhook)** 생성
4. 웹훅 이름 설정 (예: "Porcana Batch Monitor")
5. **웹훅 URL 복사** 저장

### Step 2: 환경 변수 설정

**Windows:**
```bash
set DISCORD_NOTIFICATION_ENABLED=true
set DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/your-webhook-url
```

**Linux/Mac:**
```bash
export DISCORD_NOTIFICATION_ENABLED=true
export DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/your-webhook-url
```

**application-prod.yml:**
```yaml
notification:
  discord:
    enabled: true
    webhook-url: https://discord.com/api/webhooks/your-webhook-url
```

## 2. 배치 Job에 리스너 추가

### 현재 적용된 Job
- ✅ `krAssetJob` - 한국 주식 업데이트

### 추가 필요한 Job들

각 배치 Job 클래스에 다음과 같이 리스너를 추가하세요:

```java
@Slf4j
@Configuration
@RequiredArgsConstructor
public class YourBatchJob {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    // ... other dependencies
    private final BatchNotificationListener batchNotificationListener; // 추가

    @Bean
    public Job yourJob() {
        return new JobBuilder("yourJobName", jobRepository)
                .listener(batchNotificationListener) // 추가
                .start(yourStep())
                .build();
    }
}
```

### 적용이 필요한 Job 목록

1. **UsAssetBatchJob** (`usAssetJob`)
2. **KrEtfBatchJob** (`krEtfJob`)
3. **UsEtfBatchJob** (`usEtfJob`)
4. **KrDailyPriceBatchJob** (`krDailyPriceJob`)
5. **UsDailyPriceBatchJob** (`usDailyPriceJob`)
6. **KrEtfDailyPriceBatchJob** (`krEtfDailyPriceJob`)
7. **UsEtfDailyPriceBatchJob** (`usEtfDailyPriceJob`)
8. **ExchangeRateBatchJob** (`exchangeRateJob`)
9. **AssetRiskBatchJob** (`assetRiskJob`)
10. **PortfolioPerformanceBatchJob** (`portfolioPerformanceJob`)

## 3. 알림 형식

### 성공 알림 (녹색)
```
✅ Batch Job Success
krAssetJob completed successfully

Duration: 2m 35s
Time: 2024-01-15 14:30:00
Summary:
**fetchKrAssetsStep**
- Read: 500
- Write: 500
- Skip: 0
- Commit: 10
```

### 실패 알림 (빨간색)
```
❌ Batch Job Failed
krAssetJob failed

Duration: 1m 20s
Time: 2024-01-15 14:30:00
Error:
**DataIntegrityViolationException**
```
Duplicate key value violates unique constraint
```

**Failed Step:** fetchKrAssetsStep
- Read: 250
- Write: 200
- Skip: 0
- Error: Constraint violation
```

### 경고 알림 (주황색)
```
⚠️ Batch Job Warning
krAssetJob completed with warnings

Time: 2024-01-15 14:30:00
Warning: Job status: STOPPED
Exceptions occurred during execution
```

## 4. 테스트

배치 실행 후 Discord 채널에서 알림을 확인하세요:

```bash
# 배치 실행
gradlew bootRun --args='--spring.batch.job.names=krAssetJob'
```

## 5. 커스터마이징

### 직접 알림 전송

```java
@RequiredArgsConstructor
public class YourService {

    private final DiscordNotificationService discordNotificationService;

    public void someMethod() {
        // 성공 알림
        discordNotificationService.sendBatchSuccess(
            "customJob",
            5000L,
            "Custom job completed successfully"
        );

        // 실패 알림
        discordNotificationService.sendBatchFailure(
            "customJob",
            3000L,
            "Error message here"
        );

        // 경고 알림
        discordNotificationService.sendBatchWarning(
            "customJob",
            "Warning message here"
        );
    }
}
```

## 6. 프로덕션 권장사항

1. **별도 채널 생성**: `#batch-alerts` 전용 채널 사용
2. **멘션 설정**: 실패 시 `@dev-team` 멘션 추가
3. **로그 연동**: 실패 시 스택트레이스 로그 링크 포함
4. **알람 필터링**: 성공 알림은 선택적으로 끄기
5. **Rate Limiting**: Discord API 제한 주의 (30 requests/60s)

## 7. 문제 해결

### 알림이 오지 않는 경우
1. `DISCORD_NOTIFICATION_ENABLED=true` 확인
2. Webhook URL 유효성 확인
3. 로그에서 `Discord notification sent successfully` 확인
4. Discord 채널에서 웹훅 활성화 상태 확인

### 알림이 늦게 오는 경우
- Discord API 응답 지연 가능
- 비동기 처리 고려 (향후 개선 사항)

## 8. 향후 개선 사항

- [ ] 비동기 알림 전송 (Spring @Async)
- [ ] 알림 템플릿 커스터마이징
- [ ] 슬랙(Slack) 연동 추가
- [ ] 알림 필터링 옵션 (성공/실패/경고)
- [ ] 배치 실행 통계 대시보드 연동