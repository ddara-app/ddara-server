package com.app.backend.domain.chat.presence;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** WebSocket 구독/해제/종료 이벤트로 방 presence를 갱신한다 */
@Component
public class ChatPresenceListener {

    private static final Pattern GROUP_TOPIC = Pattern.compile("^/topic/groups/(\\d+)$");

    private final ChatPresenceTracker tracker;

    public ChatPresenceListener(ChatPresenceTracker tracker) {
        this.tracker = tracker;
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        Principal user = event.getUser();
        if (destination == null || user == null) {
            return;
        }
        Matcher matcher = GROUP_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return;
        }
        tracker.enter(accessor.getSessionId(), accessor.getSubscriptionId(),
                Long.valueOf(matcher.group(1)), Long.valueOf(user.getName()));
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        tracker.leave(accessor.getSessionId(), accessor.getSubscriptionId());
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        tracker.leaveSession(event.getSessionId());
    }
}
