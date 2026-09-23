package com.app.backend.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_OAUTH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 소셜 로그인 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 refresh token 입니다."),
    TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "약관에 동의해야 합니다."),
    UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.INTERNAL_SERVER_ERROR, "지원하지 않는 소셜 로그인 제공자입니다."),
    APPLE_KEY_UNAVAILABLE(HttpStatus.INTERNAL_SERVER_ERROR, "애플 인증 키를 사용할 수 없습니다."),
    APPLE_TOKEN_EXCHANGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "애플 토큰 교환에 실패했습니다."),
    APPLE_REVOKE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "애플 연동 해제에 실패했습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    INVALID_IMAGE_FILE(HttpStatus.BAD_REQUEST, "유효하지 않은 이미지입니다."),

    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
    NOTIFICATION_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 알림에 접근할 수 없습니다."),

    INVITE_CODE_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "초대 코드 생성에 실패했습니다."),
    GROUP_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "모임은 최대 20개까지 참여할 수 있습니다."),
    INVALID_INVITE_CODE(HttpStatus.NOT_FOUND, "유효하지 않은 초대 코드입니다."),
    ALREADY_JOINED_GROUP(HttpStatus.CONFLICT, "이미 참여한 모임입니다."),
    GROUP_FULL(HttpStatus.CONFLICT, "모임 정원이 가득 찼습니다."),
    DUPLICATE_GROUP_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 모임 닉네임입니다."),
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "모임을 찾을 수 없습니다."),
    NOT_GROUP_MEMBER(HttpStatus.FORBIDDEN, "해당 모임의 멤버가 아닙니다."),

    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식입니다."),

    NOT_ENOUGH_MEMBERS(HttpStatus.CONFLICT, "회차 시작에 필요한 최소 인원(3명)에 미달합니다."),
    CYCLE_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "이미 진행 중인 회차가 있습니다."),
    CYCLE_NOT_FOUND(HttpStatus.NOT_FOUND, "회차를 찾을 수 없습니다."),
    CYCLE_CLOSED(HttpStatus.GONE, "마감된 회차입니다."),
    STARTER_CANNOT_UPLOAD(HttpStatus.CONFLICT, "스타터는 인증샷을 추가로 올릴 수 없습니다."),

    SHOT_NOT_FOUND(HttpStatus.NOT_FOUND, "사진을 찾을 수 없습니다."),
    SHOT_UNDER_REVIEW(HttpStatus.CONFLICT, "검토중인 사진은 교체할 수 없습니다."),
    CYCLE_PAUSED(HttpStatus.CONFLICT, "신고 검토중인 회차라 참여할 수 없습니다."),

    SHOT_LOCKED(HttpStatus.FORBIDDEN, "잠금 상태의 사진입니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "코멘트를 찾을 수 없습니다."),
    COMMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "본인이 작성한 코멘트가 아닙니다."),

    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "메시지를 찾을 수 없습니다."),
    NOT_MESSAGE_OWNER(HttpStatus.FORBIDDEN, "본인이 보낸 메시지가 아닙니다."),
    DUPLICATE_REACTION(HttpStatus.CONFLICT, "이미 추가한 이모지입니다."),
    REACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "추가한 리액션이 없습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}