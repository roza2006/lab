package com.medical.platform.circulation.interfaces.dto;

/**
 * 核销请求。{@code batchNo} 可省略（服务端按流转单号生成 AUTO 批次），
 * 前端核销页会传真实药品批号；{@code evidence} 为取药码/人脸流水号等核验凭证。
 */
public record RedeemRequest(String batchNo, String evidence) {
}
