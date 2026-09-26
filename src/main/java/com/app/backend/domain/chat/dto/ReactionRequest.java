package com.app.backend.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 이모지 리액션 추가/삭제 요청 */
public record ReactionRequest(
        @NotBlank @Size(max = 20) String emoji
) {
}
