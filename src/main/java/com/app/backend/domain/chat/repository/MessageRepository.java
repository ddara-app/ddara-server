package com.app.backend.domain.chat.repository;

import com.app.backend.domain.chat.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    // 이력 조회: 참여 시점 이후 + 커서(이전 id)부터 최신순. cursor null이면 최신부터
    @Query("""
            SELECT m FROM Message m
            WHERE m.groupId = :groupId
              AND m.createdAt >= :joinedAt
              AND (:cursor IS NULL OR m.id < :cursor)
              AND NOT EXISTS (SELECT 1 FROM MessageHide h WHERE h.messageId = m.id AND h.userId = :userId)
            ORDER BY m.id DESC
            """)
    List<Message> findHistory(@Param("groupId") Long groupId,
                              @Param("userId") Long userId,
                              @Param("joinedAt") LocalDateTime joinedAt,
                              @Param("cursor") Long cursor,
                              Pageable pageable);

    // 채팅방 목록용: 참여 시점 이후 마지막 메시지
    Optional<Message> findTopByGroupIdAndCreatedAtGreaterThanEqualOrderByIdDesc(Long groupId, LocalDateTime joinedAt);

    // 채팅방 목록용: 안읽음 수. lastReadAt null이면 참여 이후 전부
    @Query("""
            SELECT COUNT(m) FROM Message m
            WHERE m.groupId = :groupId
              AND m.createdAt >= :joinedAt
              AND (:lastReadAt IS NULL OR m.createdAt > :lastReadAt)
            """)
    long countUnread(@Param("groupId") Long groupId,
                     @Param("joinedAt") LocalDateTime joinedAt,
                     @Param("lastReadAt") LocalDateTime lastReadAt);
}
