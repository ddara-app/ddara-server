package com.app.backend.domain.report.client;

import com.app.backend.domain.report.entity.Report;
import com.app.backend.domain.report.entity.ReportTargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.app.backend.global.util.KstTime;

import java.util.Map;

@Component
public class DiscordReportNotifier {

    private static final Logger log = LoggerFactory.getLogger(DiscordReportNotifier.class);

    private final RestClient restClient;
    private final String webhookUrl;

    public DiscordReportNotifier(RestClient.Builder builder,
                                 @Value("${discord.report-webhook-url:}") String webhookUrl) {
        this.restClient = builder.build();
        this.webhookUrl = webhookUrl;
    }

    public void notify(Report report) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("신고 웹훅 URL 미설정 — 통지 생략: reportId={}", report.getId());
            return;
        }
        String targetLabel = switch (report.getTargetType()) {
            case COMMENT -> "코멘트";
            case MESSAGE -> "메시지";
            case USER -> "유저";
            case GROUP -> "모임";
            default -> "사진";
        };
        String content = "🚨 " + targetLabel + " 신고 접수\n"
                + targetLabel + " id: " + report.getTargetId() + "\n"
                + "사유: " + report.getReasonCode()
                + (report.getReasonText() != null ? " (" + report.getReasonText() + ")" : "") + "\n"
                + "신고자 id: " + report.getReporterId() + "\n"
                + "시각: " + KstTime.format(report.getCreatedAt());
        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("content", content))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("신고 디스코드 통지 실패: reportId={}", report.getId(), e);
        }
    }
}