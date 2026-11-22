package com.medical.platform.consultation.interfaces.dto;

import com.medical.platform.consultation.domain.MessageContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 发送消息请求，字段与 openapi.yaml 的 MessageSendRequest 对齐。
 * TEXT 的 content 是正文；IMAGE 的 content 是对象存储键。
 */
public record MessageSendRequest(
        @NotNull(message = "contentType 不能为空")
        MessageContentType contentType,

        @NotBlank(message = "消息内容不能为空")
        @Size(max = 4000, message = "消息内容不能超过 4000 字")
        String content) {
}
