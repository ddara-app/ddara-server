package com.app.backend.domain.chat.service;

import com.app.backend.domain.chat.dto.MessageResponse;
import com.app.backend.domain.chat.dto.SendMessageRequest;
import com.app.backend.domain.chat.entity.Message;
import com.app.backend.domain.chat.entity.MessageType;
import com.app.backend.domain.chat.repository.MessageRepository;
import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatMessageService {

    private final MessageRepository messageRepository;
    private final MembershipRepository membershipRepository;

    public ChatMessageService(MessageRepository messageRepository,
                              MembershipRepository membershipRepository) {
        this.messageRepository = messageRepository;
        this.membershipRepository = membershipRepository;
    }

    /** 텍스트 메시지 전송 → 저장 후 브로드캐스트할 응답 반환. 활성 멤버만 가능. */
    @Transactional
    public MessageResponse sendText(Long groupId, Long userId, SendMessageRequest request) {
        Membership membership = membershipRepository.findByGroupIdAndUserId(groupId, userId)
                .filter(Membership::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_GROUP_MEMBER));

        Message message = messageRepository.save(Message.builder()
                .groupId(groupId)
                .userId(userId)
                .type(MessageType.TEXT)
                .content(request.content())
                .build());

        return MessageResponse.of(message, membership.getNickname());
    }
}