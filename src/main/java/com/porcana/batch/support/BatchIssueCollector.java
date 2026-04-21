package com.porcana.batch.support;

import com.porcana.domain.admin.entity.BatchIssueSeverity;
import com.porcana.domain.asset.entity.Asset;
import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class BatchIssueCollector {

    private final ConcurrentMap<Long, List<CollectedIssue>> issuesByExecution = new ConcurrentHashMap<>();

    public void recordAssetIssue(Long jobExecutionId, String stepName, Asset asset,
                                 String issueCode, String issueMessage) {
        if (jobExecutionId == null || asset == null) {
            return;
        }

        recordIssue(jobExecutionId, stepName, asset.getId(), asset.getSymbol(), asset.getName(),
                issueCode, issueMessage, BatchIssueSeverity.WARNING);
    }

    public void recordIssue(Long jobExecutionId, String stepName, UUID assetId, String assetSymbol, String assetName,
                            String issueCode, String issueMessage, BatchIssueSeverity severity) {
        if (jobExecutionId == null) {
            return;
        }

        issuesByExecution.computeIfAbsent(jobExecutionId, ignored -> Collections.synchronizedList(new ArrayList<>()))
                .add(CollectedIssue.builder()
                        .stepName(stepName)
                        .assetId(assetId)
                        .assetSymbol(assetSymbol)
                        .assetName(assetName)
                        .issueCode(issueCode)
                        .issueMessage(issueMessage)
                        .severity(severity)
                        .build());
    }

    public List<CollectedIssue> drain(Long jobExecutionId) {
        if (jobExecutionId == null) {
            return List.of();
        }

        List<CollectedIssue> issues = issuesByExecution.remove(jobExecutionId);
        return issues == null ? List.of() : List.copyOf(issues);
    }

    @Getter
    @Builder
    public static class CollectedIssue {
        private final String stepName;
        private final UUID assetId;
        private final String assetSymbol;
        private final String assetName;
        private final String issueCode;
        private final String issueMessage;
        private final BatchIssueSeverity severity;
    }
}
