package com.app.backend.domain.chat.repository;

import com.app.backend.domain.chat.dto.ReactionCount;
import com.app.backend.domain.chat.entity.MessageReaction;
import com.app.backend.domain.chat.entity.MessageReactionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, MessageReactionId> {

    // 메시지의 이모지별 집계 (브로드캐스트용)
    @Query("""
            SELECT new com.app.backend.domain.chat.dto.ReactionCount(r.emoji, COUNT(r))
            FROM MessageReaction r
            WHERE r.messageId = :messageId
            GROUP BY r.emoji
            ORDER BY r.emoji
            """)
    List<ReactionCount> countByEmoji(@Param("messageId") Long messageId);
}
