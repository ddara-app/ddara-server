package com.app.backend.domain.chat.dto;

import java.util.List;

/** 채팅방 목록 조회 응답. */
public record ChatRoomListResponse(
        List<ChatRoomItem> chats
) {
}
