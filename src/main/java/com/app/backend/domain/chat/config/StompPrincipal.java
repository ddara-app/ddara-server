package com.app.backend.domain.chat.config;

import java.security.Principal;

/** WebSocket 세션에 바인딩하는 인증 주체. name에 userId를 문자열로 담는다. */
public class StompPrincipal implements Principal {

    private final String name;

    public StompPrincipal(Long userId) {
        this.name = String.valueOf(userId);
    }

    @Override
    public String getName() {
        return name;
    }
}