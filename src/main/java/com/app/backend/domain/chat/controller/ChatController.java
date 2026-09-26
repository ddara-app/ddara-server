package com.app.backend.domain.chat.controller;

import com.app.backend.domain.chat.dto.ChatMuteRequest;
import com.app.backend.domain.chat.dto.ChatMuteResponse;
import com.app.backend.domain.chat.dto.ChatReadResponse;
import com.app.backend.domain.chat.dto.ChatRoomListResponse;
import com.app.backend.domain.chat.dto.MessageHistoryResponse;
import com.app.backend.domain.chat.dto.MessageIdResponse;
import com.app.backend.domain.chat.dto.ReactionRequest;
import com.app.backend.domain.chat.dto.ReactionResponse;
import com.app.backend.domain.chat.service.ChatMessageService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    // 채팅방 목록 조회 (CHAT-02)
    @GetMapping("/api/chats")
    public ChatRoomListResponse getChatRooms(@AuthenticationPrincipal Long userId) {
        return chatMessageService.getChatRooms(userId);
    }

    // 메시지 삭제 (CHAT-04)
    @DeleteMapping("/api/messages/{messageId}")
    public MessageIdResponse deleteMessage(@AuthenticationPrincipal Long userId,
                                           @PathVariable Long messageId) {
        return chatMessageService.deleteMessage(userId, messageId);
    }

    // 메시지 숨김 (CHAT-05)
    @PostMapping("/api/messages/{messageId}/hide")
    public MessageIdResponse hideMessage(@AuthenticationPrincipal Long userId,
                                         @PathVariable Long messageId) {
        return chatMessageService.hideMessage(userId, messageId);
    }

    // 이모지 리액션 추가 (CHAT-06)
    @PostMapping("/api/messages/{messageId}/reactions")
    public ReactionResponse addReaction(@AuthenticationPrincipal Long userId,
                                        @PathVariable Long messageId,
                                        @Valid @RequestBody ReactionRequest request) {
        return chatMessageService.addReaction(userId, messageId, request.emoji());
    }

    // 이모지 리액션 삭제 (CHAT-07)
    @DeleteMapping("/api/messages/{messageId}/reactions")
    public ReactionResponse removeReaction(@AuthenticationPrincipal Long userId,
                                           @PathVariable Long messageId,
                                           @Valid @RequestBody ReactionRequest request) {
        return chatMessageService.removeReaction(userId, messageId, request.emoji());
    }

    // 방별 채팅 알림 켜기/끄기 (CHAT-08)
    @PatchMapping("/api/groups/{groupId}/chat/mute")
    public ChatMuteResponse setChatMuted(@AuthenticationPrincipal Long userId,
                                         @PathVariable Long groupId,
                                         @Valid @RequestBody ChatMuteRequest request) {
        return chatMessageService.setChatMuted(userId, groupId, request.muted());
    }
}
