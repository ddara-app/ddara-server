package com.app.backend.domain.chat.dto;

/** 이모지 리액션 추가/삭제 응답 */
public record ReactionResponse(
        Long messageId,
        String emoji
) {
}
