package com.app.backend.domain.notification.service;

import com.app.backend.domain.block.entity.Block;
import com.app.backend.domain.block.repository.BlockRepository;
import com.app.backend.domain.cycle.entity.Cycle;
import com.app.backend.domain.cycle.entity.CycleStatus;
import com.app.backend.domain.cycle.repository.CycleRepository;
import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.notification.dto.NotificationItem;
import com.app.backend.domain.notification.dto.NotificationListResponse;
import com.app.backend.domain.notification.dto.UnreadNotificationResponse;
import com.app.backend.domain.notification.entity.Notification;
import com.app.backend.domain.notification.entity.NotificationType;
import com.app.backend.domain.notification.repository.NotificationRepository;
import com.app.backend.domain.shot.entity.Shot;
import com.app.backend.domain.shot.entity.ShotType;
import com.app.backend.domain.shot.repository.ShotRepository;
import com.app.backend.domain.user.dto.NotificationSettingsResponse;
import com.app.backend.domain.user.entity.User;
import com.app.backend.domain.user.repository.UserRepository;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import com.app.backend.global.util.KstTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    // category → 포함할 type 집합
    private static final Collection<NotificationType> ACTIVITY_TYPES = EnumSet.of(
            NotificationType.NEW_CYCLE, NotificationType.CYCLE_COMPLETED, NotificationType.DEADLINE,
            NotificationType.STARTER_ASSIGNED, NotificationType.FRIEND_SHOT, NotificationType.COMMENT);
    private static final Collection<NotificationType> ETC_TYPES = EnumSet.of(
            NotificationType.MEMBER_JOIN);

    // payload에 스타터 사진이 실리는 type (cycleId로 스타터샷 조회)
    private static final Collection<NotificationType> STARTER_IMAGE_TYPES = EnumSet.of(
            NotificationType.NEW_CYCLE, NotificationType.CYCLE_COMPLETED);
    // payload에 특정 사진이 실리는 type (shotId로 조회)
    private static final Collection<NotificationType> SHOT_IMAGE_TYPES = EnumSet.of(
            NotificationType.FRIEND_SHOT, NotificationType.COMMENT);


    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final ShotRepository shotRepository;
    private final CycleRepository cycleRepository;
    private final BlockRepository blockRepository;
    private final FcmService fcmService;

    public NotificationService(NotificationRepository notificationRepository,
                               ObjectMapper objectMapper,
                               MembershipRepository membershipRepository,
                               UserRepository userRepository,
                               ShotRepository shotRepository,
                               CycleRepository cycleRepository,
                               BlockRepository blockRepository,
                               FcmService fcmService) {
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.shotRepository = shotRepository;
        this.cycleRepository = cycleRepository;
        this.blockRepository = blockRepository;
        this.fcmService = fcmService;
    }

    // ===== 알림 생성(INSERT) 내부 인터페이스 — 회차/모임 흐름(오지원)에서 호출 (부록 B) =====
    // 각 메서드: 수신자별 notification_prefs 확인 → 켜져 있으면 인앱 알림 저장 + FCM 푸시 발송.

    /** 회차 시작 → 모임 멤버 전원. */
    @Transactional
    public void createNewCycle(Long groupId, String groupName, Long cycleId,
                               Long starterUserId, LocalDateTime deadlineAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(Payload.GROUP_ID, groupId);
        payload.put(Payload.GROUP_NAME, groupName);
        payload.put(Payload.CYCLE_ID, cycleId);
        payload.put(Payload.DEADLINE_AT, KstTime.toOffset(deadlineAt).toString());   // 마감시각
        payload.put(Payload.IMAGE_URL, starterShotImageUrl(cycleId));   // 개인 관련 → 스타터 원본 가이드샷
        notifyEach(excludeBlockers(activeMemberIds(groupId), starterUserId),
                NotificationType.NEW_CYCLE, payload);
    }

    /** 회차 마감 → 모임 멤버 전원. */
    @Transactional
    public void createCycleCompleted(Long groupId, String groupName, Long cycleId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(Payload.GROUP_ID, groupId);
        payload.put(Payload.GROUP_NAME, groupName);
        payload.put(Payload.CYCLE_ID, cycleId);
        payload.put(Payload.IMAGE_URL, starterShotImageUrl(cycleId));   // 개인 관련 → 스타터 원본 가이드샷
        notifyEach(activeMemberIds(groupId), NotificationType.CYCLE_COMPLETED, payload);
    }

    /** 다음 스타터 지정 → 모임 멤버 전원. */
    @Transactional
    public void createStarterAssigned(Long groupId, String groupName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(Payload.GROUP_ID, groupId);
        payload.put(Payload.GROUP_NAME, groupName);
        payload.put(Payload.IMAGE_URL, null);
        notifyEach(activeMemberIds(groupId), NotificationType.STARTER_ASSIGNED, payload);
    }

    /** 멤버 인증샷 업로드 → 올린 사람 제외 멤버 전원 */
    @Transactional
    public void createFriendShot(Long groupId, String groupName, String actorNickname,
                                 Long uploaderUserId, Long cycleId, Long shotId) {
        List<Long> recipients = excludeBlockers(activeMemberIds(groupId), uploaderUserId).stream()
                .filter(id -> !id.equals(uploaderUserId))
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(Payload.GROUP_ID, groupId);
        payload.put(Payload.GROUP_NAME, groupName);
        payload.put(Payload.ACTOR_NICKNAME, actorNickname);
        payload.put(Payload.CYCLE_ID, cycleId);
        payload.put(Payload.SHOT_ID, shotId);
        payload.put(Payload.IMAGE_URL, shotImageUrl(shotId));
        notifyEach(recipients, NotificationType.FRIEND_SHOT, payload);
    }

    /** 코멘트 작성 → 사진 주인(isMyShot=true) + 그 사진에 이미 댓글 단 참여자(isMyShot=false) */
    @Transactional
    public void createComment(Long groupId, String groupName, String actorNickname,
                              Long shotId, Long shotOwnerUserId, String shotOwnerNickname,
                              List<Long> participantUserIds, Long commenterUserId, Long cycleId) {
        if (!shotOwnerUserId.equals(commenterUserId)) {
            List<Long> owner = excludeBlockers(List.of(shotOwnerUserId), commenterUserId);
            notifyEach(owner, NotificationType.COMMENT, commentPayload(groupId, groupName, actorNickname,
                    cycleId, shotId, shotOwnerUserId, shotOwnerNickname, true));
        }
        List<Long> participants = excludeBlockers(participantUserIds, commenterUserId).stream()
                .filter(id -> !id.equals(commenterUserId) && !id.equals(shotOwnerUserId))
                .distinct()
                .toList();
        if (!participants.isEmpty()) {
            notifyEach(participants, NotificationType.COMMENT, commentPayload(groupId, groupName, actorNickname,
                    cycleId, shotId, shotOwnerUserId, shotOwnerNickname, false));
        }
    }

    private Map<String, Object> commentPayload(Long groupId, String groupName, String actorNickname,
                                               Long cycleId, Long shotId, Long shotOwnerUserId,
                                               String shotOwnerNickname, boolean isMyShot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(Payload.GROUP_ID, groupId);
        payload.put(Payload.GROUP_NAME, groupName);
        payload.put(Payload.ACTOR_NICKNAME, actorNickname);
        payload.put(Payload.CYCLE_ID, cycleId);
        payload.put(Payload.SHOT_ID, shotId);
        payload.put(Payload.IMAGE_URL, shotImageUrl(shotId));
        payload.put(Payload.SHOT_OWNER_USER_ID, shotOwnerUserId);   // 참여자 알림에서 차단 마스킹용
        payload.put(Payload.SHOT_OWNER_NICKNAME, shotOwnerNickname);
        payload.put(Payload.IS_MY_SHOT, isMyShot);
        return payload;
    }

    /** 모임 합류 → 합류자 본인 제외 멤버 전원. */
    @Transactional
    public void createMemberJoin(Long groupId, String groupName, String actorNickname, Long joinedUserId) {
        List<Long> recipients = excludeBlockers(activeMemberIds(groupId), joinedUserId).stream()
                .filter(id -> !id.equals(joinedUserId))
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(Payload.GROUP_ID, groupId);
        payload.put(Payload.GROUP_NAME, groupName);
        payload.put(Payload.ACTOR_NICKNAME, actorNickname);
        payload.put(Payload.IMAGE_URL, null);   // 모임 관련 → 기본 아이콘 표시용 null
        notifyEach(recipients, NotificationType.MEMBER_JOIN, payload);
    }

    /**
     * 마감 임박(CycleScheduler가 매분 호출) → 아직 인증샷을 올리지 않은 미참여 멤버.
     * remainingMinutes = 남은 시간 단계(60/30/5/1분). 같은 회차·같은 단계는 1회만 발송.
     */
    @Transactional
    public void createDeadline(Long groupId, String groupName, Long cycleId,
                               LocalDateTime deadlineAt, int remainingMinutes) {
        // 스케줄러가 1분마다 재호출하므로, 같은 회차·같은 단계에 이미 생성했으면 스킵(중복 발송 방지)
        if (notificationRepository.existsDeadlineNotification(
                NotificationType.DEADLINE.name(), cycleId, remainingMinutes)) {
            return;
        }
        List<Long> recipients = activeMemberIds(groupId).stream()
                .filter(id -> !shotRepository.existsByCycleIdAndUserIdAndDeletedAtIsNull(cycleId, id))
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(Payload.GROUP_ID, groupId);
        payload.put(Payload.GROUP_NAME, groupName);
        payload.put(Payload.CYCLE_ID, cycleId);
        payload.put(Payload.REMAINING_MINUTES, remainingMinutes);
        payload.put(Payload.DEADLINE_AT, KstTime.toOffset(deadlineAt).toString());
        payload.put(Payload.IMAGE_URL, null);   // 모임 관련 → 기본 아이콘 표시용 null
        notifyEach(recipients, NotificationType.DEADLINE, payload);
    }

    /**
     * 회차 스타터가 시작 때 올린 원본 가이드샷(따라 찍을 사진) URL. 개인 관련 알림(회차시작·따라찍기완료)에 씀.
     * 해당 회차의 STARTER shot이 없거나 검토중/삭제 상태면 null.
     */
    private String starterShotImageUrl(Long cycleId) {
        if (cycleId == null) {
            return null;
        }
        return shotRepository.findByCycleIdAndType(cycleId, ShotType.STARTER)
                .filter(s -> !s.isUnderReview() && !s.isRemoved())
                .map(Shot::getImageUrl)
                .orElse(null);
    }

    /** 특정 사진의 URL */
    private String shotImageUrl(Long shotId) {
        if (shotId == null) {
            return null;
        }
        return shotRepository.findById(shotId)
                .filter(s -> !s.isUnderReview() && !s.isRemoved())
                .map(Shot::getImageUrl)
                .orElse(null);
    }

    private List<Long> activeMemberIds(Long groupId) {
        return membershipRepository.findByGroupIdAndLeftAtIsNull(groupId).stream()
                .map(Membership::getUserId)
                .toList();
    }

    /** 행위자(actor)를 차단한 유저를 수신자에서 제외 */
    private List<Long> excludeBlockers(List<Long> recipients, Long actorUserId) {
        Set<Long> blockers = blockRepository.findByBlockedId(actorUserId).stream()
                .map(Block::getBlockerId)
                .collect(Collectors.toSet());
        if (blockers.isEmpty()) {
            return recipients;
        }
        return recipients.stream()
                .filter(id -> !blockers.contains(id))
                .toList();
    }

    /** 수신자 각각에 대해 알림 설정을 확인하고, 허용되면 인앱 알림을 저장한다. */
    private void notifyEach(List<Long> userIds, NotificationType type, Map<String, Object> payload) {
        if (userIds.isEmpty()) {
            return;
        }
        String payloadJson = writePayload(payload);
        for (Long userId : userIds) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                continue;
            }
            // 인앱 알림은 알림 설정과 무관하게 항상 저장한다 — 설정을 꺼도 알림 목록엔 표시돼야 한다.
            Notification saved = notificationRepository.save(Notification.builder()
                    .userId(userId)
                    .type(type)
                    .payload(payloadJson)
                    .build());
            // 푸시(FCM)만 알림 설정을 따른다 — 꺼져 있으면 푸시는 발송하지 않는다.
            // 실패해도 예외를 던지지 않으므로(FcmService 내부 처리) 위 인앱 저장은 항상 유지된다.
            if (isAllowed(user.getNotificationPrefs(), type)) {
                fcmService.sendTo(user, pushTitle(type), pushBody(type, payload),
                        pushData(type, payload, saved.getId()));
            }
        }
    }

    /** 알림 타입별 푸시 제목. */
    private String pushTitle(NotificationType type) {
        return switch (type) {
            case NEW_CYCLE -> "새 따라찍기 시작";
            case CYCLE_COMPLETED -> "따라찍기 완료";
            case MEMBER_JOIN -> "모임 참여";
            case DEADLINE -> "마감 임박";
            case STARTER_ASSIGNED -> "랜덤 스타터";
            case FRIEND_SHOT -> "다른 친구의 따라찍기";
            case COMMENT -> "댓글";
            default -> "따라 알림";   // 2차 타입 대비
        };
    }

    /** 알림 타입별 푸시 본문. payload의 모임/합류자 이름을 활용. */
    private String pushBody(NotificationType type, Map<String, Object> payload) {
        String groupName = String.valueOf(payload.getOrDefault(Payload.GROUP_NAME, "모임"));
        return switch (type) {
            case STARTER_ASSIGNED -> "'" + groupName + "'모임의 다음 스타터가 뽑혔어요. 누구일까요?";
            case NEW_CYCLE -> "'" + groupName + "'에서 새 따라찍기가 시작됐어요!";
            case CYCLE_COMPLETED -> "'" + groupName + "'에서 따라찍기가 완료되었어요!";
            case MEMBER_JOIN -> payload.getOrDefault(Payload.ACTOR_NICKNAME, "친구") + "님이 '" + groupName + "' 모임에 합류했어요";
            case FRIEND_SHOT -> "'" + groupName + "'에서 " + payload.getOrDefault(Payload.ACTOR_NICKNAME, "친구") + "님이 따라찍기를 올렸어요";
            case COMMENT -> {
                boolean isMyShot = Boolean.TRUE.equals(payload.get(Payload.IS_MY_SHOT));
                String where = isMyShot ? "내 사진에"
                        : payload.getOrDefault(Payload.SHOT_OWNER_NICKNAME, "친구") + "님 사진에";
                yield "'" + groupName + "'에서 " + payload.getOrDefault(Payload.ACTOR_NICKNAME, "친구")
                        + "님이 " + where + " 댓글을 남겼어요";
            }
            case DEADLINE -> {
                int remaining = ((Number) payload.getOrDefault(Payload.REMAINING_MINUTES, 60)).intValue();
                String left = remaining >= 60 ? (remaining / 60) + "시간" : remaining + "분";
                yield "'" + groupName + "' 따라찍기가 " + left + " 후 마감돼요. 아직 안찍었죠?";
            }
            default -> "'" + groupName + "'에 새로운 소식이 있어요.";
        };
    }

    /** 푸시 클릭 시 앱이 화면 이동에 쓸 data(모두 문자열이어야 함 — FCM 규격). */
    private Map<String, String> pushData(NotificationType type, Map<String, Object> payload, Long notificationId) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put(FcmPushData.TYPE, type.name());
        data.put(FcmPushData.NOTIFICATION_ID, String.valueOf(notificationId));
        payload.forEach((k, v) -> {
            if (v != null) {
                data.put(k, String.valueOf(v));
            }
        });
        return data;
    }

    /** 수신자의 notification_prefs(마스터 + 타입별 토글)를 확인. 미설정/깨진 값이면 전체 허용. */
    private boolean isAllowed(String prefsJson, NotificationType type) {
        NotificationSettingsResponse prefs = parsePrefs(prefsJson);
        if (!prefs.allowAll()) {
            return false;   // 마스터 off → 아무 알림도 생성 안 함
        }
        return switch (type) {
            case NEW_CYCLE, CYCLE_COMPLETED, DEADLINE -> prefs.activity().followShot();
            case STARTER_ASSIGNED -> prefs.activity().starterAssigned();
            case FRIEND_SHOT -> prefs.activity().friendShot();
            case COMMENT -> prefs.activity().comment();
            case MEMBER_JOIN -> prefs.etc().memberJoin();
            default -> true;
        };
    }

    private NotificationSettingsResponse parsePrefs(String prefsJson) {
        if (prefsJson == null || prefsJson.isBlank()) {
            return NotificationSettingsResponse.allOn();
        }
        try {
            return NotificationSettingsResponse.fromJson(objectMapper.readTree(prefsJson));
        } catch (JsonProcessingException e) {
            return NotificationSettingsResponse.allOn();
        }
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    /** 안읽은 알림 존재 여부 */
    @Transactional(readOnly = true)
    public UnreadNotificationResponse hasUnread(Long userId) {
        return new UnreadNotificationResponse(notificationRepository.existsByUserIdAndReadAtIsNull(userId));
    }

    @Transactional(readOnly = true)
    public NotificationListResponse getNotifications(Long userId, String category) {
        // category(all/activity/etc)를 실제 알림 type 집합으로 변환
        Collection<NotificationType> types = resolveTypes(category);

        // 내(userId) 알림 중 해당 type들만, 최신순 전체 조회 → 화면용 NotificationItem으로 변환
        List<NotificationItem> items = notificationRepository
                .findByUserIdAndTypeInOrderByCreatedAtDesc(userId, types)
                .stream()
                .map(n -> toItem(n, userId))   // 알림 엔티티 → 응답 아이템(payload JSON 파싱 + 시각 +09:00 변환)
                .toList();

        // 안 읽은 알림 총 개수(뱃지용) — 필터와 무관하게 전체 기준
        long unreadCount = notificationRepository.countByUserIdAndReadAtIsNull(userId);
        return new NotificationListResponse(items, unreadCount);
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.NOTIFICATION_FORBIDDEN);   // 본인 알림만
        }

        notification.markAsRead(LocalDateTime.now());   // 이미 읽었으면 멱등(변화 없음)
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        // 안읽음이 0개여도 정상 수행(멱등)
        notificationRepository.markAllAsRead(userId, LocalDateTime.now());
    }

    // category 문자열 → 조회할 알림 type 집합
    private Collection<NotificationType> resolveTypes(String category) {
        if ("activity".equalsIgnoreCase(category)) {
            return ACTIVITY_TYPES;
        }
        if ("etc".equalsIgnoreCase(category)) {
            return ETC_TYPES;        // 기타: MEMBER_JOIN
        }
        return EnumSet.allOf(NotificationType.class);   // all(기본): 전체 type
    }

    // 알림 엔티티 1건 → 응답용 NotificationItem 1건으로 변환
    private NotificationItem toItem(Notification n, Long viewerUserId) {
        Map<String, Object> payload;   // DB엔 payload가 JSON "문자열"로 저장돼 있어서, 응답 땐 진짜 JSON 객체로 다시 파싱
        try {
            payload = objectMapper.readValue(n.getPayload(), new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException e) {
            payload = new LinkedHashMap<>();   // 혹시 payload가 깨져 있으면 빈 객체로(에러 대신)
        }
        if (STARTER_IMAGE_TYPES.contains(n.getType())) {
            applyStarterImageState(payload);
        } else if (SHOT_IMAGE_TYPES.contains(n.getType())) {
            applyShotImageState(payload, viewerUserId, n.getType() == NotificationType.FRIEND_SHOT);
        }
        return new NotificationItem(
                n.getId(),                       // 알림 id
                n.getType().name(),              // enum → 문자열 (예: "MEMBER_JOIN")
                payload,                         // 위에서 파싱한 payload 객체
                KstTime.toOffset(n.getReadAt()),      // 읽은 시각 → +09:00
                KstTime.toOffset(n.getCreatedAt()));  // 생성 시각 → +09:00
    }

    // 저장된 imageUrl은 생성 시점 값이라 조회 시점 상태(검토중/삭제)로 덮어쓴다. starterUserId는 클라 차단 마스킹용
    private void applyStarterImageState(Map<String, Object> payload) {
        Shot shot = null;
        if (payload.get(Payload.CYCLE_ID) instanceof Number cycleId) {
            shot = shotRepository.findByCycleIdAndType(cycleId.longValue(), ShotType.STARTER).orElse(null);
        }
        boolean hidden = shot == null || shot.isRemoved();
        boolean underReview = !hidden && shot.isUnderReview();
        payload.put(Payload.IMAGE_URL, hidden || underReview ? null : shot.getImageUrl());
        payload.put(Payload.IMAGE_UNDER_REVIEW, underReview);
        payload.put(Payload.STARTER_USER_ID, shot != null ? shot.getUserId() : null);
    }

    // shotId로 사진을 조회해 조회 시점 상태를 반영한다.
    private void applyShotImageState(Map<String, Object> payload, Long viewerUserId, boolean applyLock) {
        Shot shot = null;
        if (payload.get(Payload.SHOT_ID) instanceof Number shotId) {
            shot = shotRepository.findById(shotId.longValue()).orElse(null);
        }
        boolean hidden = shot == null || shot.isRemoved();
        boolean underReview = !hidden && shot.isUnderReview();
        boolean locked = applyLock && !hidden && !underReview && !canView(shot, viewerUserId);
        payload.put(Payload.IMAGE_URL, hidden || underReview ? null : shot.getImageUrl());
        payload.put(Payload.IMAGE_UNDER_REVIEW, underReview);
        payload.put(Payload.LOCKED, locked);
    }

    // SHOT-02 잠금 규칙: 마감된 회차이거나, 보는 사람이 그 회차에 사진을 올렸으면 볼 수 있다
    private boolean canView(Shot shot, Long viewerUserId) {
        Cycle cycle = cycleRepository.findById(shot.getCycleId()).orElse(null);
        if (cycle == null) {
            return false;
        }
        if (cycle.getStatus() == CycleStatus.DONE) {
            return true;
        }
        return shotRepository.findByCycleIdAndUserId(cycle.getId(), viewerUserId)
                .filter(Shot::isVisible)
                .isPresent();
    }

    private static final class Payload {
        private static final String GROUP_ID = "groupId";
        private static final String GROUP_NAME = "groupName";
        private static final String CYCLE_ID = "cycleId";
        private static final String SHOT_ID = "shotId";
        private static final String DEADLINE_AT = "deadlineAt";
        private static final String REMAINING_MINUTES = "remainingMinutes";
        private static final String IMAGE_URL = "imageUrl";
        private static final String IMAGE_UNDER_REVIEW = "imageUnderReview";
        private static final String LOCKED = "locked";
        private static final String ACTOR_NICKNAME = "actorNickname";
        private static final String STARTER_USER_ID = "starterUserId";
        private static final String SHOT_OWNER_USER_ID = "shotOwnerUserId";
        private static final String SHOT_OWNER_NICKNAME = "shotOwnerNickname";
        private static final String IS_MY_SHOT = "isMyShot";
    }

    private static final class FcmPushData {
        private static final String TYPE = "type";
        private static final String NOTIFICATION_ID = "notificationId";
    }
}
