package com.medical.platform.consultation.application;

/**
 * 发起问诊命令。字段校验在接口层 DTO 完成，应用层只做业务编排。
 */
public record CreateConsultationCommand(String department, String diseaseName, String chiefComplaint) {
}
