package com.app.backend.domain.chat.dto;

/** 방별 채팅 알림 설정 응답 */
public record ChatMuteResponse(
        Long groupId,
        boolean muted
) {
}
