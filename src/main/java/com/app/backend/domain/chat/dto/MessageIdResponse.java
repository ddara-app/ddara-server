package com.app.backend.domain.chat.dto;

/** 메시지 단건 조작(삭제, 숨김) 응답 */
public record MessageIdResponse(
        Long messageId
) {
}
