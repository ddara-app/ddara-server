package com.app.backend.domain.chat.service;

import com.app.backend.domain.chat.dto.ChatReadResponse;
import com.app.backend.domain.chat.dto.ChatRoomItem;
import com.app.backend.domain.chat.dto.ChatRoomListResponse;
import com.app.backend.domain.chat.dto.MessageHistoryItem;
import com.app.backend.domain.chat.dto.MessageHistoryResponse;
import com.app.backend.domain.chat.dto.MessageResponse;
import com.app.backend.domain.chat.dto.SendMessageRequest;
import com.app.backend.domain.chat.entity.Message;
import com.app.backend.domain.chat.entity.MessageType;
import com.app.backend.domain.chat.repository.MessageRepository;
import com.app.backend.domain.group.entity.Group;
import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.repository.GroupRepository;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import com.app.backend.global.util.KstTime;
import org.springframework.data.domain.PageRequest;
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
    private final MembershipRepository membershipRepository;
    private final GroupRepository groupRepository;

    public ChatMessageService(MessageRepository messageRepository,
                              MembershipRepository membershipRepository,
                              GroupRepository groupRepository) {
        this.messageRepository = messageRepository;
        this.membershipRepository = membershipRepository;
        this.groupRepository = groupRepository;
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

    /** 메시지 이력 조회. 참여 시점 이후 메시지만 커서 페이지네이션으로 반환. 활성 멤버만 가능. */
    @Transactional(readOnly = true)
    public MessageHistoryResponse getHistory(Long userId, Long groupId, Long cursor, int size) {
        Membership me = membershipRepository.findByGroupIdAndUserId(groupId, userId)
                .filter(Membership::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_GROUP_MEMBER));

        // 다음 페이지 존재 여부 판단을 위해 size + 1개 조회 (id 내림차순)
        List<Message> rows = messageRepository.findHistory(
                groupId, me.getJoinedAt(), cursor, PageRequest.of(0, size + 1));

        boolean hasNext = rows.size() > size;
        List<Message> page = hasNext ? rows.subList(0, size) : rows;
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;

        // 작성자 닉네임은 탈퇴한 멤버(left_at)도 포함해 조회
        List<Long> senderIds = page.stream().map(Message::getUserId).distinct().toList();
        Map<Long, String> nicknames = membershipRepository.findByGroupIdAndUserIdIn(groupId, senderIds).stream()
                .collect(Collectors.toMap(Membership::getUserId, Membership::getNickname));

        // 응답은 id 오름차순
        List<MessageHistoryItem> items = new ArrayList<>();
        for (int i = page.size() - 1; i >= 0; i--) {
            Message m = page.get(i);
            items.add(MessageHistoryItem.of(m, nicknames.get(m.getUserId())));
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
}