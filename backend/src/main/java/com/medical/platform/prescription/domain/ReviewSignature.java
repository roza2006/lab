package com.medical.platform.prescription.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 处方电子签名摘要。
 *
 * <p>签名覆盖“处方编号 + 处方 ID + 迁移前版本 + 药师 ID + 签名时间”五项，任何一项变化都会得到不同摘要，
 * 因此可以证明某位药师在某个时刻对某个版本的处方做出了审核结论。算法为 SHA-256，
 * 结果编码为 {@code SIG-} + Base64URL（无填充，43 字符），共 47 字符，可放入 {@code prescription.signed_hash CHAR(64)}。
 *
 * <p>这里只做可复核的摘要，不涉及 CA 证书与私钥签名；生产环境应替换为符合《电子签名法》的第三方签名服务，
 * 本类保持纯函数，便于后续替换实现。
 */
public final class ReviewSignature {

    private static final String PREFIX = "SIG-";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private ReviewSignature() {
    }

    /**
     * 生成审核签名摘要。
     *
     * @param prescription 待审核处方（必须在状态迁移之前调用，版本号取迁移前版本）
     * @param pharmacistId 审核药师用户 ID
     * @param signedAt     签名时间
     */
    public static String of(Prescription prescription, Long pharmacistId, LocalDateTime signedAt) {
        Objects.requireNonNull(prescription, "prescription");
        Objects.requireNonNull(pharmacistId, "pharmacistId");
        Objects.requireNonNull(signedAt, "signedAt");

        String payload = String.join("|",
                prescription.getRxNo(),
                String.valueOf(prescription.getId()),
                String.valueOf(prescription.getVersion()),
                String.valueOf(pharmacistId),
                signedAt.format(TIMESTAMP));

        byte[] digest = sha256(payload);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    /** 便于审计比对：把签名还原成可粘贴的十六进制摘要。 */
    public static String hexOf(String signature) {
        if (signature == null || !signature.startsWith(PREFIX)) {
            throw new IllegalArgumentException("不是本类生成的签名值");
        }
        byte[] raw = Base64.getUrlDecoder().decode(signature.substring(PREFIX.length()));
        return HexFormat.of().formatHex(raw);
    }

    private static byte[] sha256(String payload) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM 必须支持 SHA-256", ex);
        }
    }
}
