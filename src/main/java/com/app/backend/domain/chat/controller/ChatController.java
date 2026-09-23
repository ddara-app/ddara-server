package com.app.backend.domain.chat.controller;

import com.app.backend.domain.chat.dto.ChatReadResponse;
import com.app.backend.domain.chat.dto.MessageHistoryResponse;
import com.app.backend.domain.chat.service.ChatMessageService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final ChatMessageService chatMessageService;

    public ChatController(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }

    // 메시지 이력 조회 (CHAT-01)
    @GetMapping("/api/groups/{groupId}/messages")
    public MessageHistoryResponse getHistory(@AuthenticationPrincipal Long userId,
                                             @PathVariable Long groupId,
                                             @RequestParam(required = false) Long cursor,
                                             @RequestParam(defaultValue = "30") int size) {
        return chatMessageService.getHistory(userId, groupId, cursor, size);
    }

    // 읽음 처리 (CHAT-03)
    @PostMapping("/api/groups/{groupId}/messages/read")
    public ChatReadResponse markRead(@AuthenticationPrincipal Long userId,
                                     @PathVariable Long groupId) {
        return chatMessageService.markRead(userId, groupId);
    }
}
