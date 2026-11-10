package com.medical.platform.consultation.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.medical.platform.consultation.domain.ConsultationMessage;
import com.medical.platform.consultation.domain.ConsultationRepository;
import com.medical.platform.consultation.domain.ConsultationSession;
import com.medical.platform.consultation.domain.ConsultationStatus;
import com.medical.platform.consultation.domain.MessageContentType;
import com.medical.platform.consultation.infrastructure.mapper.ConsultationMessageMapper;
import com.medical.platform.consultation.infrastructure.mapper.ConsultationSessionMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 问诊仓储的 MyBatis 实现：负责 PO ↔ 领域对象映射，以及接诊/结束的条件更新。
 */
@Repository
public class MyBatisConsultationRepository implements ConsultationRepository {

    private final ConsultationSessionMapper sessionMapper;
    private final ConsultationMessageMapper messageMapper;

    public MyBatisConsultationRepository(ConsultationSessionMapper sessionMapper,
                                         ConsultationMessageMapper messageMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public ConsultationSession insert(ConsultationSession session) {
        ConsultationSessionPO po = new ConsultationSessionPO();
        po.setPatientId(session.getPatientId());
        po.setDoctorId(session.getDoctorId());
        po.setDepartment(session.getDepartment());
        po.setDiseaseName(session.getDiseaseName());
        po.setChiefComplaint(session.getChiefComplaint());
        po.setStatus(session.getStatus().name());
        po.setVersion(session.getVersion());
        po.setStartedAt(session.getStartedAt());
        po.setEndedAt(session.getEndedAt());
        po.setCreatedAt(session.getCreatedAt());
        sessionMapper.insert(po);
        return toDomain(po);
    }

    @Override
    public Optional<ConsultationSession> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessionMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public boolean compareAndAccept(Long id, Long doctorId, LocalDateTime startedAt, int expectedVersion) {
        int rows = sessionMapper.update(null, Wrappers.<ConsultationSessionPO>lambdaUpdate()
                .eq(ConsultationSessionPO::getId, id)
                .eq(ConsultationSessionPO::getStatus, ConsultationStatus.WAITING.name())
                .eq(ConsultationSessionPO::getVersion, expectedVersion)
                .set(ConsultationSessionPO::getStatus, ConsultationStatus.IN_PROGRESS.name())
                .set(ConsultationSessionPO::getDoctorId, doctorId)
                .set(ConsultationSessionPO::getStartedAt, startedAt)
                .setSql("version = version + 1"));
        return rows > 0;
    }

    @Override
    public boolean compareAndEnd(Long id, LocalDateTime endedAt, int expectedVersion) {
        int rows = sessionMapper.update(null, Wrappers.<ConsultationSessionPO>lambdaUpdate()
                .eq(ConsultationSessionPO::getId, id)
                .eq(ConsultationSessionPO::getVersion, expectedVersion)
                .eq(ConsultationSessionPO::getStatus, ConsultationStatus.IN_PROGRESS.name())
                .set(ConsultationSessionPO::getStatus, ConsultationStatus.ENDED.name())
                .set(ConsultationSessionPO::getEndedAt, endedAt)
                .setSql("version = version + 1"));
        return rows > 0;
    }

    @Override
    public List<ConsultationSession> findForPatient(Long patientId, ConsultationStatus status,
                                                    int offset, int size) {
        return mapSessions(sessionMapper.selectPatientPage(patientId, statusName(status), offset, size));
    }

    @Override
    public long countForPatient(Long patientId, ConsultationStatus status) {
        return sessionMapper.countPatient(patientId, statusName(status));
    }

    @Override
    public List<ConsultationSession> findForDoctor(Long doctorId, ConsultationStatus status,
                                                   int offset, int size) {
        return mapSessions(sessionMapper.selectDoctorPage(doctorId, statusName(status), offset, size));
    }

    @Override
    public long countForDoctor(Long doctorId, ConsultationStatus status) {
        return sessionMapper.countDoctor(doctorId, statusName(status));
    }

    @Override
    public List<ConsultationSession> findAll(ConsultationStatus status, int offset, int size) {
        return mapSessions(sessionMapper.selectAllPage(statusName(status), offset, size));
    }

    @Override
    public long countAll(ConsultationStatus status) {
        return sessionMapper.countAll(statusName(status));
    }

    @Override
    public ConsultationMessage appendMessage(ConsultationMessage message) {
        ConsultationMessagePO po = new ConsultationMessagePO();
        po.setSessionId(message.sessionId());
        po.setSenderId(message.senderId());
        po.setSenderRole(message.senderRole());
        po.setMsgType(message.contentType().name());
        po.setContent(message.content());
        po.setReadFlag(message.read());
        po.setSentAt(message.sentAt());
        po.setCreatedAt(message.sentAt());
        messageMapper.insert(po);
        return toDomain(po);
    }

    @Override
    public List<ConsultationMessage> findMessages(Long sessionId, int offset, int size) {
        return messageMapper.selectPage(sessionId, offset, size).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countMessages(Long sessionId) {
        return messageMapper.countBySession(sessionId);
    }

    private List<ConsultationSession> mapSessions(List<ConsultationSessionPO> rows) {
        return rows.stream().map(this::toDomain).toList();
    }

    private String statusName(ConsultationStatus status) {
        return status == null ? null : status.name();
    }

    private ConsultationSession toDomain(ConsultationSessionPO po) {
        return new ConsultationSession(
                po.getId(),
                po.getPatientId(),
                po.getDoctorId(),
                po.getDepartment(),
                po.getDiseaseName(),
                po.getChiefComplaint(),
                ConsultationStatus.valueOf(po.getStatus()),
                po.getVersion() == null ? 1 : po.getVersion(),
                po.getCreatedAt(),
                po.getStartedAt(),
                po.getEndedAt());
    }

    private ConsultationMessage toDomain(ConsultationMessagePO po) {
        return new ConsultationMessage(
                po.getId(),
                po.getSessionId(),
                po.getSenderId(),
                po.getSenderRole(),
                MessageContentType.valueOf(po.getMsgType()),
                po.getContent(),
                Boolean.TRUE.equals(po.getReadFlag()),
                po.getSentAt());
    }
}
