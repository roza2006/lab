package com.medical.platform.consultation.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 问诊会话聚合根。
 *
 * <p>状态迁移只能经 {@link #accept(Long, LocalDateTime)} 与 {@link #end(LocalDateTime)} 完成，
 * 仓储与控制器不得直接改 status；接诊并发由数据库条件更新（{@code WHERE status='WAITING' AND version=?}）
 * 兜底，这里保留内存态校验，让单元测试和非法调用尽早失败。
 *
 * <p>不变量（见 docs/design/domain-model.md §5.1）：
 * <ul>
 *   <li>I-01 只有 WAITING 可接诊，且同一会话只能有一名接诊医生；</li>
 *   <li>I-02 只有患者本人或接诊医生可追加消息，会话关闭后拒绝；</li>
 *   <li>I-04 结束后写入结束时间，会话转为只读。</li>
 * </ul>
 */
public class ConsultationSession {

    private final Long id;
    private final Long patientId;
    private Long doctorId;
    private final String department;
    private final String diseaseName;
    private final String chiefComplaint;
    private ConsultationStatus status;
    private int version;
    private final LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    public ConsultationSession(Long id,
                               Long patientId,
                               Long doctorId,
                               String department,
                               String diseaseName,
                               String chiefComplaint,
                               ConsultationStatus status,
                               int version,
                               LocalDateTime createdAt,
                               LocalDateTime startedAt,
                               LocalDateTime endedAt) {
        this.id = id;
        this.patientId = Objects.requireNonNull(patientId, "patientId");
        this.doctorId = doctorId;
        this.department = Objects.requireNonNull(department, "department");
        this.diseaseName = Objects.requireNonNull(diseaseName, "diseaseName");
        this.chiefComplaint = Objects.requireNonNull(chiefComplaint, "chiefComplaint");
        this.status = Objects.requireNonNull(status, "status");
        this.version = version;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    /** 患者发起问诊：新会话固定为 WAITING，version 从 1 开始。 */
    public static ConsultationSession open(Long patientId,
                                           String department,
                                           String diseaseName,
                                           String chiefComplaint,
                                           LocalDateTime now) {
        return new ConsultationSession(null, patientId, null, department, diseaseName,
                chiefComplaint, ConsultationStatus.WAITING, 1, now, null, null);
    }

    /** 医生接诊：只有 WAITING 可接诊，接诊后写入医生与接诊时间。 */
    public void accept(Long doctorId, LocalDateTime now) {
        if (!status.isWaiting()) {
            // 已被接诊或已结束：返回 409 而不是覆盖，避免抢单
            throw new ConsultationException(ConsultationError.SESSION_ALREADY_ACCEPTED,
                    "会话当前状态为 " + status + "，不能再接诊");
        }
        this.doctorId = Objects.requireNonNull(doctorId, "doctorId");
        this.status = ConsultationStatus.IN_PROGRESS;
        this.startedAt = now;
        this.version += 1;
    }

    /** 结束会话：关闭后只读，重复结束返回 409。 */
    public void end(LocalDateTime now) {
        if (status.isClosed()) {
            throw new ConsultationException(ConsultationError.SESSION_CLOSED,
                    "会话已结束，不能重复结束");
        }
        if (status != ConsultationStatus.IN_PROGRESS) {
            throw new ConsultationException(ConsultationError.ILLEGAL_STATE,
                    "只有进行中的会话可以结束");
        }
        this.status = ConsultationStatus.ENDED;
        this.endedAt = now;
        this.version += 1;
    }

    /** 追加消息前的写保护：会话关闭后拒绝写入。 */
    public void ensureWritable() {
        if (status.isClosed()) {
            throw new ConsultationException(ConsultationError.SESSION_CLOSED);
        }
    }

    public boolean isPatient(Long userId) {
        return patientId.equals(userId);
    }

    public boolean isDoctor(Long userId) {
        return doctorId != null && doctorId.equals(userId);
    }

    public Long getId() {
        return id;
    }

    public Long getPatientId() {
        return patientId;
    }

    public Long getDoctorId() {
        return doctorId;
    }

    public String getDepartment() {
        return department;
    }

    public String getDiseaseName() {
        return diseaseName;
    }

    public String getChiefComplaint() {
        return chiefComplaint;
    }

    public ConsultationStatus getStatus() {
        return status;
    }

    public int getVersion() {
        return version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }
}
