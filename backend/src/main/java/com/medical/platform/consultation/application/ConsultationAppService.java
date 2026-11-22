package com.medical.platform.consultation.application;

import com.medical.platform.consultation.domain.ConsultationError;
import com.medical.platform.consultation.domain.ConsultationException;
import com.medical.platform.consultation.domain.ConsultationMessage;
import com.medical.platform.consultation.domain.ConsultationRepository;
import com.medical.platform.consultation.domain.ConsultationSession;
import com.medical.platform.consultation.domain.ConsultationStatus;
import com.medical.platform.identity.application.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 问诊应用服务：会话生命周期 + 消息收发的编排与数据范围校验。
 *
 * <p>权限边界在这里做“第二次校验”：前端隐藏入口不算安全边界，所有“本人”判定都基于
 * JWT 解析出的 {@link CurrentUser#id()} 与会话归属字段。
 *
 * <p>错误码见 {@link ConsultationError}，语义与 docs/design/domain-model.md §11 一致。
 */
@Service
public class ConsultationAppService {

    private static final String ROLE_PATIENT = "PATIENT";
    private static final String ROLE_DOCTOR = "DOCTOR";
    private static final String ROLE_ADMIN = "ADMIN";

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_CONTENT_LENGTH = 4000;

    private final ConsultationRepository repository;

    public ConsultationAppService(ConsultationRepository repository) {
        this.repository = repository;
    }

    /** 患者发起问诊：创建 WAITING 会话。 */
    @Transactional
    public ConsultationView create(CurrentUser user, CreateConsultationCommand command) {
        requireRole(user, ROLE_PATIENT, "只有患者可以发起问诊");
        ConsultationSession session = ConsultationSession.open(
                user.id(),
                command.department().trim(),
                command.diseaseName().trim(),
                command.chiefComplaint().trim(),
                LocalDateTime.now());
        return ConsultationView.from(repository.insert(session));
    }

    /** 会话列表：患者看本人；医生看本人接诊 + 待接诊队列；管理员只读全量。 */
    public PageView<ConsultationView> list(CurrentUser user, String statusParam, int page, int size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        ConsultationStatus status = parseStatus(statusParam);
        int offset = safePage * safeSize;

        String role = user.roleCode();
        List<ConsultationSession> rows;
        long total;
        if (ROLE_PATIENT.equals(role)) {
            rows = repository.findForPatient(user.id(), status, offset, safeSize);
            total = repository.countForPatient(user.id(), status);
        } else if (ROLE_DOCTOR.equals(role)) {
            rows = repository.findForDoctor(user.id(), status, offset, safeSize);
            total = repository.countForDoctor(user.id(), status);
        } else if (ROLE_ADMIN.equals(role)) {
            rows = repository.findAll(status, offset, safeSize);
            total = repository.countAll(status);
        } else {
            throw new ConsultationException(ConsultationError.SCOPE_DENIED);
        }
        return PageView.of(rows.stream().map(ConsultationView::from).toList(), safePage, safeSize, total);
    }

    /** 会话详情：只有患者本人、接诊医生（或待接诊队列中的医生）、管理员可读。 */
    public ConsultationView detail(CurrentUser user, Long sessionId) {
        ConsultationSession session = load(sessionId);
        assertCanRead(user, session);
        return ConsultationView.from(session);
    }

    /** 医生接诊：WAITING → IN_PROGRESS，条件更新防并发抢单。 */
    @Transactional
    public ConsultationView accept(CurrentUser user, Long sessionId) {
        requireRole(user, ROLE_DOCTOR, "只有医生可以接诊");
        ConsultationSession session = load(sessionId);
        if (!session.getStatus().isWaiting()) {
            throw new ConsultationException(ConsultationError.SESSION_ALREADY_ACCEPTED,
                    "会话当前状态为 " + session.getStatus() + "，不能再接诊");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean updated = repository.compareAndAccept(
                sessionId, user.id(), now, session.getVersion());
        if (!updated) {
            // 并发接诊：另一个医生先成功，后到者 409，不覆盖
            throw new ConsultationException(ConsultationError.SESSION_ALREADY_ACCEPTED);
        }
        return ConsultationView.from(load(sessionId));
    }

    /** 消息历史：与会话详情同一套读取范围。 */
    public PageView<MessageView> messages(CurrentUser user, Long sessionId, int page, int size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        ConsultationSession session = load(sessionId);
        assertCanRead(user, session);

        List<MessageView> rows = repository
                .findMessages(sessionId, safePage * safeSize, safeSize)
                .stream()
                .map(MessageView::from)
                .toList();
        long total = repository.countMessages(sessionId);
        return PageView.of(rows, safePage, safeSize, total);
    }

    /** 发送消息：只有参与方可写，结束后 409 SESSION_CLOSED。 */
    @Transactional
    public MessageView send(CurrentUser user, Long sessionId, SendMessageCommand command) {
        ConsultationSession session = load(sessionId);
        assertCanWrite(user, session);
        session.ensureWritable();

        String content = command.content() == null ? "" : command.content().trim();
        if (content.isEmpty()) {
            throw new ConsultationException(ConsultationError.VALIDATION_FAILED, "消息内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new ConsultationException(ConsultationError.VALIDATION_FAILED,
                    "消息内容不能超过 " + MAX_CONTENT_LENGTH + " 字");
        }

        ConsultationMessage saved = repository.appendMessage(new ConsultationMessage(
                null,
                sessionId,
                user.id(),
                user.roleCode(),
                command.contentType(),
                content,
                false,
                LocalDateTime.now()));
        return MessageView.from(saved);
    }

    /** 结束会话：患者本人或接诊医生可结束，结束后只读。 */
    @Transactional
    public ConsultationView end(CurrentUser user, Long sessionId) {
        ConsultationSession session = load(sessionId);
        String role = user.roleCode();
        boolean allowed = (ROLE_PATIENT.equals(role) && session.isPatient(user.id()))
                || (ROLE_DOCTOR.equals(role) && session.isDoctor(user.id()));
        if (!allowed) {
            throw new ConsultationException(ConsultationError.SCOPE_DENIED);
        }

        int expectedVersion = session.getVersion();
        session.end(LocalDateTime.now());
        boolean updated = repository.compareAndEnd(sessionId, session.getEndedAt(), expectedVersion);
        if (!updated) {
            throw new ConsultationException(ConsultationError.ILLEGAL_STATE,
                    "会话状态已被其他操作改变，请刷新后重试");
        }
        return ConsultationView.from(load(sessionId));
    }

    // ------------------------------------------------------------------
    // 内部：加载、角色与数据范围校验、参数归一
    // ------------------------------------------------------------------

    private ConsultationSession load(Long sessionId) {
        if (sessionId == null || sessionId <= 0) {
            throw new ConsultationException(ConsultationError.CONSULTATION_NOT_FOUND);
        }
        return repository.findById(sessionId)
                .orElseThrow(() -> new ConsultationException(ConsultationError.CONSULTATION_NOT_FOUND));
    }

    private void assertCanRead(CurrentUser user, ConsultationSession session) {
        String role = user.roleCode();
        if (ROLE_PATIENT.equals(role)) {
            if (!session.isPatient(user.id())) {
                throw new ConsultationException(ConsultationError.SCOPE_DENIED);
            }
            return;
        }
        if (ROLE_DOCTOR.equals(role)) {
            // 待接诊会话对医生可见（候诊队列），已接诊的只对本人可见
            if (session.isDoctor(user.id()) || session.getStatus().isWaiting()) {
                return;
            }
            throw new ConsultationException(ConsultationError.SCOPE_DENIED);
        }
        if (ROLE_ADMIN.equals(role)) {
            return;
        }
        throw new ConsultationException(ConsultationError.SCOPE_DENIED);
    }

    private void assertCanWrite(CurrentUser user, ConsultationSession session) {
        String role = user.roleCode();
        if (ROLE_PATIENT.equals(role) && session.isPatient(user.id())) {
            return;
        }
        if (ROLE_DOCTOR.equals(role) && session.isDoctor(user.id())) {
            return;
        }
        throw new ConsultationException(ConsultationError.SCOPE_DENIED);
    }

    private void requireRole(CurrentUser user, String expected, String message) {
        if (!expected.equals(user.roleCode())) {
            throw new ConsultationException(ConsultationError.SCOPE_DENIED, message);
        }
    }

    private ConsultationStatus parseStatus(String statusParam) {
        if (statusParam == null || statusParam.isBlank()) {
            return null;
        }
        try {
            return ConsultationStatus.valueOf(statusParam.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ConsultationException(ConsultationError.BAD_REQUEST,
                    "不支持的会话状态：" + statusParam);
        }
    }

    private int normalizePage(int page) {
        if (page < 0) {
            throw new ConsultationException(ConsultationError.BAD_REQUEST, "page 不能小于 0");
        }
        return page;
    }

    private int normalizeSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ConsultationException(ConsultationError.BAD_REQUEST,
                    "size 必须在 1 到 " + MAX_PAGE_SIZE + " 之间");
        }
        return size;
    }
}
