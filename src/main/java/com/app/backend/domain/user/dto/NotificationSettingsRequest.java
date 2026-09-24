package com.app.backend.domain.user.dto;

import jakarta.validation.Valid;

public record NotificationSettingsRequest(
        Boolean allowAll,
        @Valid Activity activity,
        @Valid Etc etc
) {
    public record Activity(
            Boolean followShot,
            Boolean friendShot,
            Boolean starterAssigned,
            Boolean comment,
            Boolean chat
    ) {
    }

    public record Etc(
            Boolean memberJoin
    ) {
    }
}
