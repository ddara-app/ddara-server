package com.app.backend.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 채팅 텍스트 메시지 전송 요청. */
public record SendMessageRequest(
        @NotBlank @Size(max = 200) String content
) {
}