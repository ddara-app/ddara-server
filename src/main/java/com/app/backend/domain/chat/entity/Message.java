package com.app.backend.domain.chat.entity;

import com.app.backend.domain.shot.entity.ReviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageType type;

    @Column(length = 200)
    private String content;

    // photo(사진 답글)/starter_share가 인용하는 사진
    @Column(name = "shot_id")
    private Long shotId;

    // 꾸민 이미지 URL (image 타입)
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // 답글 대상 메시지
    @Column(name = "reply_to_message_id")
    private Long replyToMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // 내 메시지 삭제 = 모두에게 삭제
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private Message(Long groupId, Long userId, MessageType type, String content,
                    Long shotId, String imageUrl, Long replyToMessageId) {
        this.groupId = groupId;
        this.userId = userId;
        this.type = type;
        this.content = content;
        this.shotId = shotId;
        this.imageUrl = imageUrl;
        this.replyToMessageId = replyToMessageId;
        this.reviewStatus = ReviewStatus.ACTIVE;
    }
}