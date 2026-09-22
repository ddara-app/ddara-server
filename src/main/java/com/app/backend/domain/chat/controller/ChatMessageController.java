package com.app.backend.domain.chat.controller;

import com.app.backend.domain.chat.dto.ChatEvent;
import com.app.backend.domain.chat.dto.MessageResponse;
import com.app.backend.domain.chat.dto.SendMessageRequest;
import com.app.backend.domain.chat.service.ChatMessageService;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import com.app.backend.global.exception.ErrorResponse;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatMessageController(ChatMessageService chatMessageService,
                                 SimpMessagingTemplate messagingTemplate) {
        this.chatMessageService = chatMessageService;
        this.messagingTemplate = messagingTemplate;
    }

    // 앱 → /app/groups/{groupId}/messages 로 전송 → 저장 후 구독자에게 브로드캐스트
    @MessageMapping("/groups/{groupId}/messages")
    public void send(@DestinationVariable Long groupId,
                     @Valid @Payload SendMessageRequest request,
                     Principal principal) {
        Long userId = Long.valueOf(principal.getName());
        MessageResponse response = chatMessageService.sendText(groupId, userId, request);
        messagingTemplate.convertAndSend("/topic/groups/" + groupId, ChatEvent.newMessage(response));
    }

    // 전송 처리 중 예외는 요청자 개인 큐(/user/queue/errors)로 {code, message} 전달
    @MessageExceptionHandler(CustomException.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleCustom(CustomException e) {
        return ErrorResponse.of(e.getErrorCode());
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleValidation(MethodArgumentNotValidException e) {
        return ErrorResponse.of(ErrorCode.INVALID_INPUT);
    }
}