package com.app.backend.domain.chat.dto;

import java.util.List;

/** 메시지 이력 조회 응답. 커서 페이지네이션. */
public record MessageHistoryResponse(
        List<MessageHistoryItem> messages,
        boolean hasNext,
        Long nextCursor
) {
}
