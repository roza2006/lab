package com.medical.platform.consultation.domain;

/**
 * 问诊会话状态（对齐 openapi.yaml 的 ConsultationStatus 与 domain-model.md §8.2）。
 *
 * <pre>
 * WAITING ──accept──▶ IN_PROGRESS ──end──▶ ENDED ──archive──▶ ARCHIVED
 * </pre>
 */
public enum ConsultationStatus {

    /** 待接诊：患者已提交主诉，等待医生接诊。 */
    WAITING,
    /** 进行中：已被医生接诊，双方可收发消息。 */
    IN_PROGRESS,
    /** 已结束：会话关闭，只读。 */
    ENDED,
    /** 已归档：超过保留期的只读状态。 */
    ARCHIVED;

    /** 会话是否已关闭（关闭后禁止追加消息、禁止再次接诊/结束）。 */
    public boolean isClosed() {
        return this == ENDED || this == ARCHIVED;
    }

    public boolean isWaiting() {
        return this == WAITING;
    }
}
