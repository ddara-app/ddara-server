package com.app.backend.domain.group.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "memberships")
@IdClass(MembershipId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Membership {

    @Id
    @Column(name = "group_id")
    private Long groupId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 10)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MembershipRole role;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "starter_seen_at")
    private LocalDateTime starterSeenAt;

    @Column(name = "chat_last_read_at")
    private LocalDateTime chatLastReadAt;

    @Column(name = "chat_muted", nullable = false)
    private boolean chatMuted;

    @Builder
    private Membership(Long groupId, Long userId, String nickname, MembershipRole role, LocalDateTime joinedAt) {
        this.groupId = groupId;
        this.userId = userId;
        this.nickname = nickname;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public boolean isActive() {
        return leftAt == null;
    }

    // 모임 나가기: left_at에 시각 기록 (soft delete)
    public void leave(LocalDateTime leftAt) {
        this.leftAt = leftAt;
    }

    // 나갔던 멤버가 다시 합류: 새 row 대신 left_at을 NULL로 복귀 (닉네임 재입력)
    public void rejoin(LocalDateTime joinedAt, String nickname) {
        this.joinedAt = joinedAt;
        this.nickname = nickname;
        this.leftAt = null;
    }

    // 모임 내 닉네임 변경
    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    // 룰렛 열람 기록
    public void markStarterSeen(LocalDateTime seenAt) {
        this.starterSeenAt = seenAt;
    }

    // 채팅방 읽음 처리
    public void markChatRead(LocalDateTime readAt) {
        this.chatLastReadAt = readAt;
    }

    // 방별 채팅 알림 켜기/끄기 (muted=true면 알림 끔)
    public void changeChatMuted(boolean muted) {
        this.chatMuted = muted;
    }
}