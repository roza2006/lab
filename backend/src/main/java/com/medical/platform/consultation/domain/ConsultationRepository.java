package com.medical.platform.consultation.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 问诊聚合的仓储端口。领域层只依赖接口，MyBatis 实现在 infrastructure 包。
 */
public interface ConsultationRepository {

    ConsultationSession insert(ConsultationSession session);

    Optional<ConsultationSession> findById(Long id);

    /**
     * 乐观锁接诊：只有 status=WAITING 且 version 匹配时才更新。
     *
     * @return 影响行数为 0 表示已被其他医生接诊或版本已变
     */
    boolean compareAndAccept(Long id, Long doctorId, LocalDateTime startedAt, int expectedVersion);

    /** 乐观锁结束会话，返回是否成功。 */
    boolean compareAndEnd(Long id, LocalDateTime endedAt, int expectedVersion);

    List<ConsultationSession> findForPatient(Long patientId, ConsultationStatus status, int offset, int size);

    long countForPatient(Long patientId, ConsultationStatus status);

    List<ConsultationSession> findForDoctor(Long doctorId, ConsultationStatus status, int offset, int size);

    long countForDoctor(Long doctorId, ConsultationStatus status);

    List<ConsultationSession> findAll(ConsultationStatus status, int offset, int size);

    long countAll(ConsultationStatus status);

    ConsultationMessage appendMessage(ConsultationMessage message);

    List<ConsultationMessage> findMessages(Long sessionId, int offset, int size);

    long countMessages(Long sessionId);
}
