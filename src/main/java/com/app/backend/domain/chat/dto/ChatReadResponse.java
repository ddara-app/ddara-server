package com.app.backend.domain.chat.dto;

import java.time.OffsetDateTime;

/** 읽음 처리 응답. 갱신된 마지막 읽은 시각. */
public record ChatReadResponse(
        OffsetDateTime chatLastReadAt
) {
}
