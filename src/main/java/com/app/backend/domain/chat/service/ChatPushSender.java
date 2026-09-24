package com.app.backend.domain.chat.service;

import com.app.backend.domain.chat.entity.Message;
import com.app.backend.domain.chat.entity.MessageType;
import com.app.backend.domain.chat.presence.ChatPresenceTracker;
import com.app.backend.domain.group.entity.Group;
import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.repository.GroupRepository;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.notification.service.FcmService;
import com.app.backend.domain.user.dto.NotificationSettingsResponse;
import com.app.backend.domain.user.entity.User;
import com.app.backend.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 새 메시지 발생 시 방 밖 멤버에게 채팅 푸시(FCM)를 보낸다 */
@Component
public class ChatPushSender {

    private static final Logger log = LoggerFactory.getLogger(ChatPushSender.class);

    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final FcmService fcmService;
    private final ChatPresenceTracker presenceTracker;
    private final ObjectMapper objectMapper;

    public ChatPushSender(MembershipRepository membershipRepository,
                          UserRepository userRepository,
                          GroupRepository groupRepository,
                          FcmService fcmService,
                          ChatPresenceTracker presenceTracker,
                          ObjectMapper objectMapper) {
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.fcmService = fcmService;
        this.presenceTracker = presenceTracker;
        this.objectMapper = objectMapper;
    }

    public void pushNewMessage(Message message, String senderNickname) {
        Long groupId = message.getGroupId();
        Long senderId = message.getUserId();

        // 보낸 사람 제외 + 방별 알림 켜짐 + 방 밖(presence)
        List<Long> candidates = membershipRepository.findByGroupIdAndLeftAtIsNull(groupId).stream()
                .filter(m -> !m.getUserId().equals(senderId))
                .filter(m -> !m.isChatMuted())
                .filter(m -> !presenceTracker.isInRoom(groupId, m.getUserId()))
                .map(Membership::getUserId)
                .toList();
        if (candidates.isEmpty()) {
            return;
        }

        // 전역 채팅 알림 켜진 사용자만
        Map<Long, User> users = userRepository.findAllById(candidates).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<User> recipients = candidates.stream()
                .map(users::get)
                .filter(u -> u != null && chatEnabled(u))
                .toList();
        if (recipients.isEmpty()) {
            return;
        }

        String groupName = groupRepository.findById(groupId).map(Group::getName).orElse("모임");
        String body = senderNickname + ": " + preview(message);
        Map<String, String> data = Map.of("type", "CHAT", "groupId", String.valueOf(groupId));

        log.info("채팅 푸시 group={} 대상={}", groupId, recipients.stream().map(User::getId).toList());
        recipients.forEach(user -> fcmService.sendTo(user, groupName, body, data));
    }

    private String preview(Message message) {
        return switch (message.getType()) {
            case IMAGE -> "이미지를 보냈습니다";
            case STARTER_SHARE -> "스타터 사진을 공유했습니다";
            case TEXT, PHOTO -> message.getContent();
        };
    }

    private boolean chatEnabled(User user) {
        NotificationSettingsResponse prefs = parsePrefs(user.getNotificationPrefs());
        return prefs.allowAll() && prefs.activity().chat();
    }

    private NotificationSettingsResponse parsePrefs(String prefsJson) {
        if (prefsJson == null || prefsJson.isBlank()) {
            return NotificationSettingsResponse.allOn();
        }
        try {
            return NotificationSettingsResponse.fromJson(objectMapper.readTree(prefsJson));
        } catch (Exception e) {
            return NotificationSettingsResponse.allOn();
        }
    }
}
