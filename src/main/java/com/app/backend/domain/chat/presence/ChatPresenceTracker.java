package com.app.backend.domain.chat.presence;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 채팅방 구독자(현재 방에 들어와 있는 사용자)를 in-memory로 추적한다 */
@Component
public class ChatPresenceTracker {

    private record Sub(Long groupId, Long userId) {
    }

    // sessionId:subscriptionId → Sub (unsubscribe/disconnect 시 역추적용)
    private final Map<String, Sub> subscriptions = new ConcurrentHashMap<>();
    // groupId → (userId → 활성 구독 수)
    private final Map<Long, Map<Long, Integer>> roomUsers = new ConcurrentHashMap<>();

    public void enter(String sessionId, String subscriptionId, Long groupId, Long userId) {
        subscriptions.put(key(sessionId, subscriptionId), new Sub(groupId, userId));
        roomUsers.computeIfAbsent(groupId, g -> new ConcurrentHashMap<>()).merge(userId, 1, Integer::sum);
    }

    public void leave(String sessionId, String subscriptionId) {
        Sub sub = subscriptions.remove(key(sessionId, subscriptionId));
        if (sub != null) {
            decrement(sub.groupId(), sub.userId());
        }
    }

    public void leaveSession(String sessionId) {
        String prefix = sessionId + ":";
        subscriptions.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(prefix)) {
                decrement(entry.getValue().groupId(), entry.getValue().userId());
                return true;
            }
            return false;
        });
    }

    public boolean isInRoom(Long groupId, Long userId) {
        Map<Long, Integer> users = roomUsers.get(groupId);
        return users != null && users.containsKey(userId);
    }

    private void decrement(Long groupId, Long userId) {
        roomUsers.computeIfPresent(groupId, (g, users) -> {
            users.computeIfPresent(userId, (u, count) -> count <= 1 ? null : count - 1);
            return users.isEmpty() ? null : users;
        });
    }

    private String key(String sessionId, String subscriptionId) {
        return sessionId + ":" + subscriptionId;
    }
}
