package com.app.backend.domain.chat.config;

import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import com.app.backend.global.exception.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/** CONNECT, SUBSCRIBE 처리 중 예외를 ERROR 프레임에 {code, message}로 담아 전달한다. */
@Component
public class StompErrorHandler extends StompSubProtocolErrorHandler {

    private final ObjectMapper objectMapper;

    public StompErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
        CustomException custom = findCustom(ex);
        if (custom == null) {
            return super.handleClientMessageProcessingError(clientMessage, ex);
        }
        return errorFrame(custom.getErrorCode());
    }

    private CustomException findCustom(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof CustomException custom) {
                return custom;
            }
        }
        return null;
    }

    private Message<byte[]> errorFrame(ErrorCode errorCode) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(errorCode.name());
        accessor.setContentType(MimeTypeUtils.APPLICATION_JSON);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(toJson(errorCode), accessor.getMessageHeaders());
    }

    private byte[] toJson(ErrorCode errorCode) {
        try {
            return objectMapper.writeValueAsBytes(ErrorResponse.of(errorCode));
        } catch (JsonProcessingException e) {
            return new byte[0];
        }
    }
}
