package com.medical.platform.consultation.application;

import com.medical.platform.consultation.domain.ConsultationMessage;

import java.time.LocalDateTime;

/**
 * 消息对外视图，字段与 openapi.yaml 的 Message schema 对齐。
 */
public record MessageView(Long id,
                          Long consultationId,
                          Long senderId,
                          String senderRole,
                          String contentType,
                          String content,
                          boolean read,
                          LocalDateTime sentAt) {

    public static MessageView from(ConsultationMessage message) {
        return new MessageView(
                message.id(),
                message.sessionId(),
                message.senderId(),
                message.senderRole(),
                message.contentType().name(),
                message.content(),
                message.read(),
                message.sentAt());
    }
}
