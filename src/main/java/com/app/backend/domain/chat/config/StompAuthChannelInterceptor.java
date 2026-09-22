package com.app.backend.domain.chat.config;

import com.app.backend.domain.auth.jwt.JwtProvider;
import com.app.backend.domain.group.repository.MembershipRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** CONNECT 시 JWT 인증, SUBSCRIBE 시 모임 멤버 인가. */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern GROUP_TOPIC = Pattern.compile("^/topic/groups/(\\d+)$");

    private final JwtProvider jwtProvider;
    private final MembershipRepository membershipRepository;

    public StompAuthChannelInterceptor(JwtProvider jwtProvider,
                                       MembershipRepository membershipRepository) {
        this.jwtProvider = jwtProvider;
        this.membershipRepository = membershipRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            String token = extractToken(accessor);
            if (token == null || !jwtProvider.validateToken(token)) {
                throw new MessagingException("인증 실패");
            }
            accessor.setUser(new StompPrincipal(jwtProvider.getUserId(token)));
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            Long groupId = parseGroupId(accessor.getDestination());
            if (groupId != null && !membershipRepository
                    .existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, currentUserId(accessor))) {
                throw new MessagingException("해당 모임 멤버가 아닙니다");
            }
        }
        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private Long currentUserId(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new MessagingException("인증되지 않은 연결");
        }
        return Long.valueOf(accessor.getUser().getName());
    }

    private Long parseGroupId(String destination) {
        if (destination == null) {
            return null;
        }
        Matcher matcher = GROUP_TOPIC.matcher(destination);
        return matcher.matches() ? Long.valueOf(matcher.group(1)) : null;
    }
}