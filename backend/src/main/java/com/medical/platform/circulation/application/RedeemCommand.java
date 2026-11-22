package com.medical.platform.circulation.application;

/**
 * 核销命令：药品批号与核验凭证。批号为空时由服务端按流转单号生成 AUTO 批次。
 */
public record RedeemCommand(String batchNo, String evidence) {
}
