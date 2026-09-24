package com.app.backend.domain.user.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 알림 설정(U-03/04). users.notification_prefs(JSON)와 동일 구조.
 * 미설정(null)이거나 일부 필드가 없으면 해당 필드는 true로 채운다.
 */
public record NotificationSettingsResponse(
        boolean allowAll,
        Activity activity,
        Etc etc
) {
    public record Activity(boolean followShot, boolean friendShot, boolean starterAssigned, boolean comment, boolean chat) {
    }

    public record Etc(boolean memberJoin) {
    }

    /** 미설정 기본값 — 전부 켜짐(true). */
    public static NotificationSettingsResponse allOn() {
        return new NotificationSettingsResponse(true,
                new Activity(true, true, true, true, true), new Etc(true));
    }

    public static NotificationSettingsResponse fromJson(JsonNode root) {
        if (root == null || root.isNull()) {
            return allOn();
        }
        JsonNode activity = root.path("activity");
        JsonNode etc = root.path("etc");
        return new NotificationSettingsResponse(
                boolOrTrue(root, "allowAll"),
                new Activity(
                        boolOrTrue(activity, "followShot"),
                        boolOrTrue(activity, "friendShot"),
                        boolOrTrue(activity, "starterAssigned"),
                        boolOrTrue(activity, "comment"),
                        boolOrTrue(activity, "chat")),
                new Etc(boolOrTrue(etc, "memberJoin")));
    }

    private static boolean boolOrTrue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? true : value.asBoolean(true);
    }
}
