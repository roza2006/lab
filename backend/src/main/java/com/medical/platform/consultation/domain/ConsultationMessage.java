package com.medical.platform.consultation.domain;

import java.time.LocalDateTime;

/**
 * 问诊消息。图片消息的 content 存对象存储键，不存二进制。
 */
public record ConsultationMessage(Long id,
                                  Long sessionId,
                                  Long senderId,
                                  String senderRole,
                                  MessageContentType contentType,
                                  String content,
                                  boolean read,
                                  LocalDateTime sentAt) {
}
