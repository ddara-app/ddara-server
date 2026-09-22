package com.app.backend.domain.chat.dto;

/** 브로드캐스트 공통 형식. event로 종류 구분, data에 실제 내용. */
public record ChatEvent(String event, Object data) {

    public static ChatEvent newMessage(Object data) {
        return new ChatEvent("NEW_MESSAGE", data);
    }
}
