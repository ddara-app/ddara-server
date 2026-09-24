package com.app.backend.domain.chat.dto;

import jakarta.validation.constraints.NotNull;

/** 방별 채팅 알림 켜기/끄기 요청. muted=true면 알림 끔. */
public record ChatMuteRequest(
        @NotNull Boolean muted
) {
}
