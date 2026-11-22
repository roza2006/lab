package com.medical.platform.consultation.domain;

/**
 * 消息内容类型。TEXT 存正文，IMAGE 存对象存储键（不把二进制落库）。
 */
public enum MessageContentType {
    TEXT,
    IMAGE
}
