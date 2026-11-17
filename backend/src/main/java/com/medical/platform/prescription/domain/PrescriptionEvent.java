package com.medical.platform.prescription.domain;

/**
 * 处方状态机上的事件。状态与事件的对应关系集中在
 * {@link PrescriptionStatus#canApply(PrescriptionEvent)}，聚合方法只负责守卫和副作用。
 */
public enum PrescriptionEvent {

    SUBMIT,
    APPROVE,
    REJECT,
    REVISE,
    CANCEL,
    CIRCULATE,
    REDEEM,
    EXPIRE
}
