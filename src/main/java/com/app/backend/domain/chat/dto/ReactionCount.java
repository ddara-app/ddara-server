package com.app.backend.domain.chat.dto;

/** 메시지의 이모지별 리액션 집계 */
public record ReactionCount(
        String emoji,
        long count
) {
}
