package com.app.backend.domain.cycle.service;

import com.app.backend.domain.chat.service.ChatMessageService;
import com.app.backend.domain.cycle.dto.CreateCycleRequest;
import com.app.backend.domain.cycle.dto.CycleCreateResponse;
import com.app.backend.domain.cycle.dto.PastCyclesResponse;
import com.app.backend.domain.cycle.entity.Cycle;
import com.app.backend.domain.cycle.entity.CycleStatus;
import com.app.backend.domain.cycle.repository.CycleRepository;
import com.app.backend.domain.group.entity.Group;
import com.app.backend.domain.group.repository.GroupRepository;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.group.service.NextStarterAssigner;
import com.app.backend.domain.notification.service.NotificationService;
import com.app.backend.domain.shot.entity.Shot;
import com.app.backend.domain.shot.entity.ShotType;
import com.app.backend.domain.shot.repository.ShotRepository;
import com.app.backend.domain.user.entity.User;
import com.app.backend.domain.user.repository.UserRepository;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import com.app.backend.global.util.KstTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CycleService {

    private static final int MIN_MEMBERS_TO_START = 2;
    private static final int CYCLE_DURATION_HOURS = 24;

    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final CycleRepository cycleRepository;
    private final ShotRepository shotRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final NextStarterAssigner nextStarterAssigner;
    private final ChatMessageService chatMessageService;

    public CycleService(GroupRepository groupRepository,
                        MembershipRepository membershipRepository,
                        CycleRepository cycleRepository,
                        ShotRepository shotRepository,
                        NotificationService notificationService,
                        UserRepository userRepository,
                        NextStarterAssigner nextStarterAssigner,
                        ChatMessageService chatMessageService) {
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.cycleRepository = cycleRepository;
        this.shotRepository = shotRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.nextStarterAssigner = nextStarterAssigner;
        this.chatMessageService = chatMessageService;
    }

    @Transactional
    public CycleCreateResponse createCycle(Long userId, Long groupId, CreateCycleRequest request) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_NOT_FOUND));
        if (!membershipRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId)) {
            throw new CustomException(ErrorCode.NOT_GROUP_MEMBER);
        }
        if (membershipRepository.countByGroupIdAndLeftAtIsNull(groupId) < MIN_MEMBERS_TO_START) {
            throw new CustomException(ErrorCode.NOT_ENOUGH_MEMBERS);
        }
        if (cycleRepository.existsByGroupIdAndStatus(groupId, CycleStatus.IN_PROGRESS)) {
            throw new CustomException(ErrorCode.CYCLE_ALREADY_IN_PROGRESS);
        }

        LocalDateTime now = LocalDateTime.now();
        int cycleNumber = (int) cycleRepository.countByGroupId(groupId) + 1;

        Cycle cycle = cycleRepository.save(Cycle.builder()
                .groupId(groupId)
                .cycleNumber(cycleNumber)
                .topic(request.topic())
                .starterUserId(userId)
                .startedAt(now)
                .deadlineAt(now.plusHours(CYCLE_DURATION_HOURS))
                .build());

        // 스타터 원본 사진을 Shot(type=starter)으로 함께 저장 (따라찍기 가이드)
        Shot starterShot = shotRepository.save(Shot.builder()
                .cycleId(cycle.getId())
                .userId(userId)
                .type(ShotType.STARTER)
                .imageUrl(request.imageUrl())
                .build());

        if (!userId.equals(group.getNextStarterUserId())) {
            group.clearNextStarter();
        }

        notificationService.createNewCycle(groupId, group.getName(), cycle.getId(), userId, cycle.getDeadlineAt());

        // 스타터 사진을 채팅방에 자동 공유
        chatMessageService.shareStarterShot(groupId, userId, starterShot.getId());

        return CycleCreateResponse.of(cycle, starterShot);
    }

    @Transactional(readOnly = true)
    public PastCyclesResponse getPastCycles(Long userId, Long groupId, Integer year, Integer month) {
        if (!groupRepository.existsById(groupId)) {
            throw new CustomException(ErrorCode.GROUP_NOT_FOUND);
        }
        if (!membershipRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId)) {
            throw new CustomException(ErrorCode.NOT_GROUP_MEMBER);
        }
        if ((year == null) != (month == null) || (month != null && (month < 1 || month > 12))) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        List<Cycle> doneCycles = cycleRepository
                .findByGroupIdAndStatusOrderByCycleNumberDesc(groupId, CycleStatus.DONE);
        if (year != null) {
            LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0);
            LocalDateTime end = start.plusMonths(1);
            doneCycles = doneCycles.stream()
                    .filter(c -> !c.getStartedAt().isBefore(start) && c.getStartedAt().isBefore(end))
                    .toList();
        }

        // 업로드순으로 조회
        Map<Long, List<Shot>> shotsByCycle = new LinkedHashMap<>();
        for (Cycle cycle : doneCycles) {
            List<Shot> shots = shotRepository.findByCycleIdAndDeletedAtIsNull(cycle.getId()).stream()
                    .filter(s -> !s.isRemoved())
                    .sorted(Comparator.comparing(Shot::getUploadedAt,
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                    .toList();
            shotsByCycle.put(cycle.getId(), shots);
        }
        Map<Long, User> usersById = userRepository.findAllById(
                        shotsByCycle.values().stream()
                                .flatMap(List::stream)
                                .map(Shot::getUserId)
                                .distinct()
                                .toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        long myCount = 0;
        List<PastCyclesResponse.PastCycle> cycles = new ArrayList<>();
        for (Cycle cycle : doneCycles) {
            List<Shot> shots = shotsByCycle.get(cycle.getId());
            if (shots.stream().anyMatch(s -> s.getUserId().equals(userId))) {
                myCount++;
            }
            Optional<Shot> starterShot = shotRepository
                    .findByCycleIdAndType(cycle.getId(), ShotType.STARTER);
            boolean underReview = starterShot.map(Shot::isUnderReview).orElse(false);
            String thumbnailUrl = underReview ? null
                    : starterShot.map(Shot::getImageUrl).orElse(null);
            List<PastCyclesResponse.Participant> participants = shots.stream()
                    .map(s -> new PastCyclesResponse.Participant(
                            s.getUserId(),
                            Optional.ofNullable(usersById.get(s.getUserId()))
                                    .map(User::getProfileImageUrl)
                                    .orElse(null)))
                    .toList();
            cycles.add(new PastCyclesResponse.PastCycle(
                    cycle.getId(),
                    cycle.getTopic(),
                    thumbnailUrl,
                    underReview,
                    cycle.getStarterUserId(),
                    shots.size(),
                    participants,
                    KstTime.toOffset(cycle.getStartedAt())));
        }

        return new PastCyclesResponse(
                new PastCyclesResponse.Stats(myCount, doneCycles.size()), cycles);
    }

    // 시작 후 24h(deadline) 지난 진행 중 회차를 일괄 마감 (스케줄러용)
    @Transactional
    public int closeOverdueCycles() {
        List<Cycle> overdue = cycleRepository
                .findByStatusAndDeadlineAtBefore(CycleStatus.IN_PROGRESS, LocalDateTime.now());
        for (Cycle cycle : overdue) {
            cycle.complete();
            // 회차 마감 → 모임 멤버 전원에게 인앱 알림 + FCM 푸시 (#77)
            notificationService.createCycleCompleted(
                    cycle.getGroupId(), groupName(cycle.getGroupId()), cycle.getId());
            nextStarterAssigner.assignAfterCycleClosed(cycle.getGroupId(), cycle.getStarterUserId());
        }
        return overdue.size();
    }

    private static final int[] DEADLINE_STAGES_MINUTES = {60, 30, 5, 1};

    // 마감 임박(1시간 내) 회차의 미참여 멤버에게 단계별(60/30/5/1분) 임박 알림 (스케줄러용)
    @Transactional
    public int notifyUpcomingDeadlines() {
        LocalDateTime now = LocalDateTime.now();
        List<Cycle> closing = cycleRepository.findByStatusAndDeadlineAtBetween(
                CycleStatus.IN_PROGRESS, now, now.plusMinutes(60));
        for (Cycle cycle : closing) {
            for (int stage : DEADLINE_STAGES_MINUTES) {
                // 남은 시간이 stage분 이하가 됐으면 해당 단계 알림 발송(단계별 1회 보장)
                if (!cycle.getDeadlineAt().isAfter(now.plusMinutes(stage))) {
                    notificationService.createDeadline(
                            cycle.getGroupId(), groupName(cycle.getGroupId()),
                            cycle.getId(), cycle.getDeadlineAt(), stage);
                }
            }
        }
        return closing.size();
    }

    private String groupName(Long groupId) {
        return groupRepository.findById(groupId).map(Group::getName).orElse("모임");
    }
}
