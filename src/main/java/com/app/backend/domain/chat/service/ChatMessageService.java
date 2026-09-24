package com.app.backend.domain.chat.service;

import com.app.backend.domain.chat.dto.ChatEvent;
import com.app.backend.domain.chat.dto.ChatMuteResponse;
import com.app.backend.domain.chat.dto.ChatReadResponse;
import com.app.backend.domain.chat.dto.ChatRoomItem;
import com.app.backend.domain.chat.dto.ChatRoomListResponse;
import com.app.backend.domain.chat.dto.MessageHistoryItem;
import com.app.backend.domain.chat.dto.MessageHistoryResponse;
import com.app.backend.domain.chat.dto.MessageIdResponse;
import com.app.backend.domain.chat.dto.MessageResponse;
import com.app.backend.domain.chat.dto.ReactionCount;
import com.app.backend.domain.chat.dto.ReactionResponse;
import com.app.backend.domain.chat.dto.SendMessageRequest;
import com.app.backend.domain.chat.entity.Message;
import com.app.backend.domain.chat.entity.MessageHide;
import com.app.backend.domain.chat.entity.MessageHideId;
import com.app.backend.domain.chat.entity.MessageReaction;
import com.app.backend.domain.chat.entity.MessageReactionId;
import com.app.backend.domain.chat.entity.MessageType;
import com.app.backend.domain.chat.repository.MessageHideRepository;
import com.app.backend.domain.chat.repository.MessageReactionRepository;
import com.app.backend.domain.chat.repository.MessageRepository;
import com.app.backend.domain.cycle.entity.Cycle;
import com.app.backend.domain.cycle.repository.CycleRepository;
import com.app.backend.domain.group.entity.Group;
import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.repository.GroupRepository;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.shot.entity.Shot;
import com.app.backend.domain.shot.repository.ShotRepository;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import com.app.backend.global.util.KstTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatMessageService {

    private final MessageRepository messageRepository;
    private final MessageHideRepository messageHideRepository;
    private final MessageReactionRepository messageReactionRepository;
    private final MembershipRepository membershipRepository;
    private final GroupRepository groupRepository;
    private final ShotRepository shotRepository;
    private final CycleRepository cycleRepository;
    private final ChatPushSender chatPushSender;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatMessageService(MessageRepository messageRepository,
                              MessageHideRepository messageHideRepository,
                              MessageReactionRepository messageReactionRepository,
                              MembershipRepository membershipRepository,
                              GroupRepository groupRepository,
                              ShotRepository shotRepository,
                              CycleRepository cycleRepository,
                              ChatPushSender chatPushSender,
                              SimpMessagingTemplate messagingTemplate) {
        this.messageRepository = messageRepository;
        this.messageHideRepository = messageHideRepository;
        this.messageReactionRepository = messageReactionRepository;
        this.membershipRepository = membershipRepository;
        this.groupRepository = groupRepository;
        this.shotRepository = shotRepository;
        this.cycleRepository = cycleRepository;
        this.chatPushSender = chatPushSender;
        this.messagingTemplate = messagingTemplate;
    }

    /** 메시지 전송 → type별 검증·저장 후 imageUrl 등을 채운 응답 반환. 활성 멤버만 가능. */
    @Transactional
    public MessageResponse send(Long groupId, Long userId, SendMessageRequest request) {
        Membership membership = membershipRepository.findByGroupIdAndUserId(groupId, userId)
                .filter(Membership::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_GROUP_MEMBER));

        MessageType type = request.type() == null ? MessageType.TEXT : request.type();
        Message message = switch (type) {
            case TEXT -> buildText(groupId, userId, request);
            case PHOTO -> buildPhoto(groupId, userId, request);
            case IMAGE -> buildImage(groupId, userId, request);
            default -> throw new CustomException(ErrorCode.INVALID_INPUT);
        };

        Message saved = messageRepository.save(message);
        chatPushSender.pushNewMessage(saved, membership.getNickname());
        return toResponse(saved, membership.getNickname());
    }

    /** 회차 시작 시 스타터 사진을 채팅방에 자동 공유한다. (shot 도메인에서 호출) */
    @Transactional
    public void shareStarterShot(Long groupId, Long starterUserId, Long shotId) {
        Message saved = messageRepository.save(Message.builder()
                .groupId(groupId).userId(starterUserId)
                .type(MessageType.STARTER_SHARE).shotId(shotId)
                .build());
        String nickname = membershipRepository.findByGroupIdAndUserId(groupId, starterUserId)
                .map(Membership::getNickname).orElse(null);
        messagingTemplate.convertAndSend("/topic/groups/" + groupId,
                ChatEvent.newMessage(toResponse(saved, nickname)));
    }

    private Message buildText(Long groupId, Long userId, SendMessageRequest request) {
        requireContent(request);
        return Message.builder()
                .groupId(groupId).userId(userId)
                .type(MessageType.TEXT).content(request.content())
                .build();
    }

    private Message buildPhoto(Long groupId, Long userId, SendMessageRequest request) {
        requireContent(request);
        if (request.shotId() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        if (!shotRepository.existsById(request.shotId())) {
            throw new CustomException(ErrorCode.SHOT_NOT_FOUND);
        }
        return Message.builder()
                .groupId(groupId).userId(userId)
                .type(MessageType.PHOTO).content(request.content()).shotId(request.shotId())
                .build();
    }

    private Message buildImage(Long groupId, Long userId, SendMessageRequest request) {
        if (request.imageUrl() == null || request.imageUrl().isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return Message.builder()
                .groupId(groupId).userId(userId)
                .type(MessageType.IMAGE).imageUrl(request.imageUrl())
                .build();
    }

    private void requireContent(SendMessageRequest request) {
        if (request.content() == null || request.content().isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    // 단건 응답: imageUrl(IMAGE는 저장값, PHOTO/STARTER_SHARE는 shotId로 사진 조회)과 topic(STARTER_SHARE) 해결
    private MessageResponse toResponse(Message message, String senderNickname) {
        Shot shot = null;
        if ((message.getType() == MessageType.PHOTO || message.getType() == MessageType.STARTER_SHARE)
                && message.getShotId() != null) {
            shot = shotRepository.findById(message.getShotId()).orElse(null);
        }
        String imageUrl = message.getType() == MessageType.IMAGE
                ? message.getImageUrl()
                : (shot == null ? null : shot.getImageUrl());
        String topic = (message.getType() == MessageType.STARTER_SHARE && shot != null)
                ? cycleRepository.findById(shot.getCycleId()).map(Cycle::getTopic).orElse(null)
                : null;
        return MessageResponse.of(message, senderNickname, imageUrl, topic);
    }

    /** 메시지 이력 조회. 참여 시점 이후 메시지만 커서 페이지네이션으로 반환. 활성 멤버만 가능. */
    @Transactional(readOnly = true)
    public MessageHistoryResponse getHistory(Long userId, Long groupId, Long cursor, int size) {
        Membership me = membershipRepository.findByGroupIdAndUserId(groupId, userId)
                .filter(Membership::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_GROUP_MEMBER));

        // 다음 페이지 존재 여부 판단을 위해 size + 1개 조회 (id 내림차순). 숨긴 메시지 제외.
        List<Message> rows = messageRepository.findHistory(
                groupId, userId, me.getJoinedAt(), cursor, PageRequest.of(0, size + 1));

        boolean hasNext = rows.size() > size;
        List<Message> page = hasNext ? rows.subList(0, size) : rows;
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;

        // 작성자 닉네임은 탈퇴한 멤버(left_at)도 포함해 조회
        List<Long> senderIds = page.stream().map(Message::getUserId).distinct().toList();
        Map<Long, String> nicknames = membershipRepository.findByGroupIdAndUserIdIn(groupId, senderIds).stream()
                .collect(Collectors.toMap(Membership::getUserId, Membership::getNickname));

        // 이미지 메시지의 shotId → 사진 배치 조회
        List<Long> shotIds = page.stream().map(Message::getShotId).filter(id -> id != null).distinct().toList();
        Map<Long, Shot> shots = shotRepository.findAllById(shotIds).stream()
                .collect(Collectors.toMap(Shot::getId, s -> s));

        // STARTER_SHARE의 회차 주제 배치 조회
        List<Long> cycleIds = page.stream()
                .filter(m -> m.getType() == MessageType.STARTER_SHARE && shots.containsKey(m.getShotId()))
                .map(m -> shots.get(m.getShotId()).getCycleId())
                .distinct().toList();
        Map<Long, String> topics = cycleRepository.findAllById(cycleIds).stream()
                .collect(Collectors.toMap(Cycle::getId, Cycle::getTopic));

        // 응답은 id 오름차순
        List<MessageHistoryItem> items = new ArrayList<>();
        for (int i = page.size() - 1; i >= 0; i--) {
            Message m = page.get(i);
            Shot shot = m.getShotId() == null ? null : shots.get(m.getShotId());
            String imageUrl = m.getType() == MessageType.IMAGE
                    ? m.getImageUrl()
                    : (shot == null ? null : shot.getImageUrl());
            String topic = (m.getType() == MessageType.STARTER_SHARE && shot != null)
                    ? topics.get(shot.getCycleId())
                    : null;
            items.add(MessageHistoryItem.of(m, nicknames.get(m.getUserId()), imageUrl, topic));
        }
        return new MessageHistoryResponse(items, hasNext, nextCursor);
    }

    /** 읽음 처리 → chat_last_read_at을 현재 시각으로 갱신. 활성 멤버만 가능 */
    @Transactional
    public ChatReadResponse markRead(Long userId, Long groupId) {
        Membership me = membershipRepository.findByGroupIdAndUserId(groupId, userId)
                .filter(Membership::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_GROUP_MEMBER));

        LocalDateTime now = LocalDateTime.now();
        me.markChatRead(now);
        return new ChatReadResponse(KstTime.toOffset(now));
    }

    /** 방별 채팅 알림 켜기/끄기 */
    @Transactional
    public ChatMuteResponse setChatMuted(Long userId, Long groupId, boolean muted) {
        Membership me = membershipRepository.findByGroupIdAndUserId(groupId, userId)
                .filter(Membership::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_GROUP_MEMBER));
        me.changeChatMuted(muted);
        return new ChatMuteResponse(groupId, muted);
    }

    /** 채팅방 목록 조회. 참여 중인 방마다 최근 메시지와 안읽음 수를 포함. 최근 메시지 순 정렬 */
    @Transactional(readOnly = true)
    public ChatRoomListResponse getChatRooms(Long userId) {
        List<Membership> memberships = membershipRepository.findByUserIdAndLeftAtIsNull(userId);
        List<Long> groupIds = memberships.stream().map(Membership::getGroupId).toList();
        Map<Long, Group> groups = groupRepository.findAllById(groupIds).stream()
                .filter(g -> g.getDeletedAt() == null)
                .collect(Collectors.toMap(Group::getId, g -> g));

        List<ChatRoomItem> items = new ArrayList<>();
        for (Membership m : memberships) {
            Group group = groups.get(m.getGroupId());
            if (group == null) {
                continue;
            }
            Message last = messageRepository
                    .findTopByGroupIdAndCreatedAtGreaterThanEqualOrderByIdDesc(m.getGroupId(), m.getJoinedAt())
                    .orElse(null);
            long unread = messageRepository.countUnread(m.getGroupId(), m.getJoinedAt(), m.getChatLastReadAt());
            items.add(new ChatRoomItem(
                    group.getId(),
                    group.getName(),
                    last == null ? null : last.getContent(),
                    last == null ? null : KstTime.toOffset(last.getCreatedAt()),
                    unread));
        }
        // 최근 메시지 순(내림차순)
        items.sort(Comparator.comparing(ChatRoomItem::lastMessageAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return new ChatRoomListResponse(items);
    }

    /** 메시지 삭제. 본인 메시지만 soft delete 후 삭제 이벤트를 구독자에게 전송 */
    @Transactional
    public MessageIdResponse deleteMessage(Long userId, Long messageId) {
        Message message = messageRepository.findById(messageId)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new CustomException(ErrorCode.MESSAGE_NOT_FOUND));
        if (!message.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_MESSAGE_OWNER);
        }
        message.markDeleted(LocalDateTime.now());
        messagingTemplate.convertAndSend("/topic/groups/" + message.getGroupId(),
                ChatEvent.messageDeleted(messageId));
        return new MessageIdResponse(messageId);
    }

    /** 메시지 숨김. 본인 화면에서만 제외 */
    @Transactional
    public MessageIdResponse hideMessage(Long userId, Long messageId) {
        if (!messageRepository.existsById(messageId)) {
            throw new CustomException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        MessageHideId hideId = new MessageHideId(messageId, userId);
        if (!messageHideRepository.existsById(hideId)) {
            messageHideRepository.save(MessageHide.builder().messageId(messageId).userId(userId).build());
        }
        return new MessageIdResponse(messageId);
    }

    /** 이모지 리액션 추가. 다중 이모지 허용, 같은 이모지 중복 불가. 갱신 이벤트 전송 */
    @Transactional
    public ReactionResponse addReaction(Long userId, Long messageId, String emoji) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new CustomException(ErrorCode.MESSAGE_NOT_FOUND));
        MessageReactionId id = new MessageReactionId(messageId, userId, emoji);
        if (messageReactionRepository.existsById(id)) {
            throw new CustomException(ErrorCode.DUPLICATE_REACTION);
        }
        messageReactionRepository.save(MessageReaction.builder()
                .messageId(messageId).userId(userId).emoji(emoji).build());
        broadcastReactions(message);
        return new ReactionResponse(messageId, emoji);
    }

    /** 이모지 리액션 삭제. 갱신 이벤트 전송 */
    @Transactional
    public ReactionResponse removeReaction(Long userId, Long messageId, String emoji) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new CustomException(ErrorCode.MESSAGE_NOT_FOUND));
        MessageReactionId id = new MessageReactionId(messageId, userId, emoji);
        if (!messageReactionRepository.existsById(id)) {
            throw new CustomException(ErrorCode.REACTION_NOT_FOUND);
        }
        messageReactionRepository.deleteById(id);
        broadcastReactions(message);
        return new ReactionResponse(messageId, emoji);
    }

    // 리액션 집계를 구독자에게 전송
    private void broadcastReactions(Message message) {
        List<ReactionCount> reactions = messageReactionRepository.countByEmoji(message.getId());
        messagingTemplate.convertAndSend("/topic/groups/" + message.getGroupId(),
                ChatEvent.reactionUpdated(message.getId(), reactions));
    }
}