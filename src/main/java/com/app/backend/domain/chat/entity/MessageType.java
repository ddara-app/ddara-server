package com.app.backend.domain.chat.entity;

// 채팅 메시지 종류. photo=사진 답글(shot 인용), image=꾸민 이미지, starter_share=회차 시작 버블
public enum MessageType {
    TEXT, PHOTO, IMAGE, STARTER_SHARE
}