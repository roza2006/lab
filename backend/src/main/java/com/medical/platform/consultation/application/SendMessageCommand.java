package com.medical.platform.consultation.application;

import com.medical.platform.consultation.domain.MessageContentType;

/**
 * 发送消息命令。
 */
public record SendMessageCommand(MessageContentType contentType, String content) {
}
