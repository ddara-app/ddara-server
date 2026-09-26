package com.app.backend.domain.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "message_reactions")
@IdClass(MessageReactionId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageReaction {

    @Id
    @Column(name = "message_id")
    private Long messageId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(length = 20)
    private String emoji;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private MessageReaction(Long messageId, Long userId, String emoji) {
        this.messageId = messageId;
        this.userId = userId;
        this.emoji = emoji;
    }
}
