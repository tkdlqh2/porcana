package com.porcana.global.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Discord Webhook notification service
 * Sends notifications to Discord channel via webhook
 */
@Slf4j
@Service
public class DiscordNotificationService {

    private final RestTemplate restTemplate;
    private final String webhookUrl;
    private final boolean enabled;

    public DiscordNotificationService(
            RestTemplate restTemplate,
            @Value("${notification.discord.webhook-url:}") String webhookUrl,
            @Value("${notification.discord.enabled:false}") boolean enabled
    ) {
        this.restTemplate = restTemplate;
        this.webhookUrl = webhookUrl;
        this.enabled = enabled;

        if (enabled && (webhookUrl == null || webhookUrl.isBlank())) {
            log.warn("Discord notifications enabled but webhook URL not configured");
        }
    }

    /**
     * Send batch job success notification
     */
    public void sendBatchSuccess(String jobName, long durationMs, String summary) {
        if (!enabled) {
            return;
        }

        Map<String, Object> embed = createEmbed(
                "✅ Batch Job Success",
                String.format("**%s** completed successfully", jobName),
                0x00FF00, // Green
                    List.of(
                            createField("Duration", formatDuration(durationMs), true),
                            createField("Time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), true),
                            createField("Summary", truncate(summary, 1000), false)
                )
        );

        sendWebhook(embed);
    }

    /**
     * Send batch job failure notification
     */
    public void sendBatchFailure(String jobName, long durationMs, String errorMessage) {
        if (!enabled) {
            return;
        }

        Map<String, Object> embed = createEmbed(
                "❌ Batch Job Failed",
                String.format("**%s** failed", jobName),
                0xFF0000, // Red
                List.of(
                        createField("Duration", formatDuration(durationMs), true),
                        createField("Time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), true),
                        createField("Error", truncate(errorMessage, 1000), false)
                )
        );

        sendWebhook(embed);
    }

    /**
     * Send batch job warning notification
     */
    public void sendBatchWarning(String jobName, String message) {
        if (!enabled) {
            return;
        }

        Map<String, Object> embed = createEmbed(
                "⚠️ Batch Job Warning",
                String.format("**%s** completed with warnings", jobName),
                0xFFA500, // Orange
                List.of(
                        createField("Time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), true),
                        createField("Warning", truncate(message, 1000), false)
                )
        );

        sendWebhook(embed);
    }

    /**
     * Send custom notification
     */
    public void sendNotification(String title, String description, int color, List<Map<String, Object>> fields) {
        if (!enabled) {
            return;
        }

        Map<String, Object> embed = createEmbed(title, description, color, fields);
        sendWebhook(embed);
    }

    /**
     * Create Discord embed object
     */
    private Map<String, Object> createEmbed(String title, String description, int color, List<Map<String, Object>> fields) {
        Map<String, Object> embed = new HashMap<>();
        embed.put("title", title);
        embed.put("description", description);
        embed.put("color", color);
        embed.put("fields", fields);
        embed.put("timestamp", LocalDateTime.now().toString());
        embed.put("footer", Map.of("text", "Porcana Batch System"));

        return embed;
    }

    /**
     * Create embed field
     */
    private Map<String, Object> createField(String name, String value, boolean inline) {
        Map<String, Object> field = new HashMap<>();
        field.put("name", name);
        field.put("value", value);
        field.put("inline", inline);
        return field;
    }

    /**
     * Send webhook to Discord
     */
    private void sendWebhook(Map<String, Object> embed) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("Discord webhook URL not configured, skipping notification");
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("embeds", List.of(embed));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(webhookUrl, request, String.class);
            log.debug("Discord notification sent successfully");

        } catch (Exception e) {
            log.error("Failed to send Discord notification: {}", e.getMessage());
        }
    }

    /**
     * Format duration in human-readable format
     */
    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * Truncate string to max length
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return "N/A";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }
}