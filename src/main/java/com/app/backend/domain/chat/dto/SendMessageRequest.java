package com.app.backend.domain.chat.dto;

import com.app.backend.domain.chat.entity.MessageType;
import jakarta.validation.constraints.Size;

/** 채팅 메시지 전송 요청 */
public record SendMessageRequest(
        MessageType type,
        @Size(max = 200) String content,
        Long shotId
) {
}
