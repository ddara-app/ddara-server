package com.app.backend.domain.chat.repository;

import com.app.backend.domain.chat.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    // 이력 조회: 참여 시점 이후 + 커서(이전 id)부터 최신순. cursor null이면 최신부터
    @Query("""
            SELECT m FROM Message m
            WHERE m.groupId = :groupId
              AND m.createdAt >= :joinedAt
              AND (:cursor IS NULL OR m.id < :cursor)
            ORDER BY m.id DESC
            """)
    List<Message> findHistory(@Param("groupId") Long groupId,
                              @Param("joinedAt") LocalDateTime joinedAt,
                              @Param("cursor") Long cursor,
                              Pageable pageable);
}
