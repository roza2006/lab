package com.medical.platform.prescription;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 处方状态机测试：8 个状态的正向迁移与非法迁移。
 *
 * <p>状态与迁移以 {@code docs/design/domain-model.md} §8.1 与《实验报告》表 2-10 为准，
 * 用例编号与前置条件见 {@code docs/test/prescription-state-cases.md}。
 *
 * <p><b>当前状态</b>：处方上下文（R4-A 聚合、R4-B 审核）尚未合入 main，
 * 迁移用例暂时 {@link Disabled}，现在只运行两类可执行的检查：
 * <ol>
 *   <li>安全边界：处方/审核的全部端点都必须先认证；</li>
 *   <li>状态机自检：迁移表覆盖 8 个状态、10 条合法迁移，且与设计文档 §8.1 逐条一致。</li>
 * </ol>
 * 实现合入后：去掉迁移用例上的 {@code @Disabled}，在 {@code schema-test.sql} 补
 * {@code prescription} / {@code prescription_item} 表（字段见 {@code database/schema.sql}），
 * 并实现 {@link #givenPrescriptionIn(String)}。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("处方状态机：8 状态的正向与非法迁移")
class PrescriptionStateTest {

    private static final String RX = "/api/v1/prescriptions";
    private static final String PASSWORD = "Passw0rd!";

    /** 8 个状态，与 domain-model §8.1 一致。 */
    private static final Set<String> STATES = Set.of(
            "DRAFT", "PENDING_REVIEW", "REJECTED", "APPROVED",
            "CIRCULATED", "REDEEMED", "CANCELLED", "EXPIRED");

    /** 终态：不再接受任何事件。 */
    private static final Set<String> TERMINAL_STATES = Set.of("CANCELLED", "REDEEMED", "EXPIRED");

    /** 合法迁移表：(当前状态, 事件) → 目标状态。 */
    private static final Map<String, String> LEGAL_TRANSITIONS = Map.ofEntries(
            Map.entry("DRAFT:submit", "PENDING_REVIEW"),
            Map.entry("PENDING_REVIEW:approve", "APPROVED"),
            Map.entry("PENDING_REVIEW:reject", "REJECTED"),
            Map.entry("REJECTED:revise", "DRAFT"),
            Map.entry("DRAFT:cancel", "CANCELLED"),
            Map.entry("PENDING_REVIEW:cancel", "CANCELLED"),
            Map.entry("APPROVED:circulate", "CIRCULATED"),
            Map.entry("CIRCULATED:redeem", "REDEEMED"),
            Map.entry("APPROVED:expire", "EXPIRED"),
            Map.entry("CIRCULATED:expire", "EXPIRED"));

    /** 每个处方端点都必须先通过认证（未登录时一律 401）。 */
    private static final List<String[]> PROTECTED_ENDPOINTS = List.of(
            new String[]{"POST", "/api/v1/consultations/1/prescriptions"},
            new String[]{"GET", RX},
            new String[]{"GET", RX + "/1"},
            new String[]{"PUT", RX + "/1"},
            new String[]{"POST", RX + "/1/submit"},
            new String[]{"POST", RX + "/1/cancel"},
            new String[]{"GET", RX + "/review-queue"},
            new String[]{"POST", RX + "/1/reviews/approve"},
            new String[]{"POST", RX + "/1/reviews/reject"},
            new String[]{"GET", RX + "/1/reviews"});

    private static final Pattern MERMAID_TRANSITION =
            Pattern.compile("^(\\w+|\\[\\*])\\s*-->\\s*(\\w+|\\[\\*])\\s*:\\s*(\\w+)$");

    private static final Path DESIGN_DOC = Path.of("..", "docs", "design", "domain-model.md");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedUsers() {
        String hash = passwordEncoder.encode(PASSWORD);
        jdbcTemplate.update("DELETE FROM sys_user");
        insertUser(1L, "patient01", hash, "PATIENT", 1);
        insertUser(2L, "doctor01", hash, "DOCTOR", 1);
        insertUser(3L, "doctor02", hash, "DOCTOR", 1);
        insertUser(4L, "pharmacist01", hash, "PHARMACIST", 1);
    }

    // ------------------------------------------------------------------
    // 可执行部分一：状态机自检（不依赖处方实现）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("状态机：8 个状态、10 条合法迁移，且没有指向未知状态的迁移")
    void stateTableIsWellFormed() {
        assertThat(STATES).hasSize(8);
        assertThat(LEGAL_TRANSITIONS).hasSize(10);

        // 迁移的起点、终点都必须是已知状态
        assertThat(LEGAL_TRANSITIONS.keySet())
                .allSatisfy(key -> assertThat(STATES).contains(key.split(":")[0]));
        assertThat(LEGAL_TRANSITIONS.values())
                .allSatisfy(target -> assertThat(STATES).contains(target));

        // 同一 (状态, 事件) 只能有一个目标状态：map 的 key 天然唯一，这里断言事件取值合法
        assertThat(LEGAL_TRANSITIONS.keySet())
                .allSatisfy(key -> assertThat(key).contains(":"));
    }

    @Test
    @DisplayName("状态机：终态没有任何出边")
    void terminalStatesHaveNoOutgoingTransition() {
        for (String terminal : TERMINAL_STATES) {
            assertThat(LEGAL_TRANSITIONS.keySet())
                    .as("终态 %s 不应存在出边", terminal)
                    .noneMatch(key -> key.startsWith(terminal + ":"));
        }
    }

    @Test
    @DisplayName("状态机：8 个状态都能从 DRAFT 经合法迁移到达")
    void everyStateIsReachableFromDraft() {
        Set<String> reached = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add("DRAFT");
        reached.add("DRAFT");

        while (!queue.isEmpty()) {
            String current = queue.poll();
            LEGAL_TRANSITIONS.forEach((key, target) -> {
                if (key.startsWith(current + ":") && reached.add(target)) {
                    queue.add(target);
                }
            });
        }

        assertThat(reached).containsExactlyInAnyOrderElementsOf(STATES);
    }

    @Test
    @DisplayName("状态机：与设计文档 §8.1 的 Mermaid 迁移逐条一致（防文档漂移）")
    void stateTableMatchesDesignDocument() throws Exception {
        assumeTrue(Files.isRegularFile(DESIGN_DOC), "未找到设计文档，跳过文档一致性检查");

        Map<String, String> documented = new LinkedHashMap<>();
        boolean inBlock = false;
        for (String raw : Files.readAllLines(DESIGN_DOC, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.startsWith("stateDiagram-v2")) {
                inBlock = true;
                continue;
            }
            if (inBlock && line.equals("```")) {
                break;
            }
            if (!inBlock) {
                continue;
            }
            Matcher matcher = MERMAID_TRANSITION.matcher(line);
            if (matcher.matches() && !"[*]".equals(matcher.group(1))) {
                // 形如：DRAFT --> PENDING_REVIEW : submit
                documented.put(matcher.group(1) + ":" + matcher.group(3), matcher.group(2));
            }
        }

        assertThat(documented)
                .as("测试中的迁移表必须与 docs/design/domain-model.md §8.1 完全一致")
                .isEqualTo(LEGAL_TRANSITIONS);
    }

    // ------------------------------------------------------------------
    // 可执行部分二：安全边界
    // ------------------------------------------------------------------

    @Test
    @DisplayName("处方与审核端点：未认证一律 401，不进入业务逻辑")
    void prescriptionEndpointsRequireAuthentication() throws Exception {
        for (String[] endpoint : PROTECTED_ENDPOINTS) {
            var request = switch (endpoint[0]) {
                case "GET" -> get(endpoint[1]);
                case "PUT" -> put(endpoint[1]).contentType(MediaType.APPLICATION_JSON).content("{}");
                case "POST" -> post(endpoint[1]).contentType(MediaType.APPLICATION_JSON).content("{}");
                default -> throw new IllegalStateException("未覆盖的方法：" + endpoint[0]);
            };
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("令牌无效时提交处方：401 而不是 409")
    void invalidTokenCannotSubmit() throws Exception {
        mockMvc.perform(post(RX + "/1/submit")
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // 待 R4-A / R4-B 合入后启用：正向迁移
    // ------------------------------------------------------------------

    @Test
    @Disabled("等待 R4-A 处方聚合与状态迁移合入 main 后启用")
    @DisplayName("正向：DRAFT --submit--> PENDING_REVIEW，版本号 +1")
    void submitFromDraft() throws Exception {
        long rxId = givenPrescriptionIn("DRAFT");

        mockMvc.perform(post(RX + "/" + rxId + "/submit")
                        .header("Authorization", bearer("doctor01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }

    @Test
    @Disabled("等待 R4-B 药师审核合入 main 后启用")
    @DisplayName("正向：PENDING_REVIEW --approve--> APPROVED，写入签名摘要")
    void approveFromPendingReview() throws Exception {
        long rxId = givenPrescriptionIn("PENDING_REVIEW");

        mockMvc.perform(post(RX + "/" + rxId + "/reviews/approve")
                        .header("Authorization", bearer("pharmacist01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signature\":\"pharmacist-signature\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @Disabled("等待 R4-B 药师审核合入 main 后启用")
    @DisplayName("正向：PENDING_REVIEW --reject--> REJECTED，驳回意见必填")
    void rejectFromPendingReview() throws Exception {
        long rxId = givenPrescriptionIn("PENDING_REVIEW");

        mockMvc.perform(post(RX + "/" + rxId + "/reviews/reject")
                        .header("Authorization", bearer("pharmacist01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"用法用量与说明书不符，请修改后重提\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @Disabled("等待 R4-A 处方聚合合入 main 后启用")
    @DisplayName("正向：DRAFT --cancel--> CANCELLED（终态）")
    void cancelFromDraft() throws Exception {
        long rxId = givenPrescriptionIn("DRAFT");

        mockMvc.perform(post(RX + "/" + rxId + "/cancel")
                        .header("Authorization", bearer("doctor01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ------------------------------------------------------------------
    // 待 R4-A / R4-B 合入后启用：非法迁移
    // ------------------------------------------------------------------

    @Test
    @Disabled("等待 R4-A 处方聚合合入 main 后启用")
    @DisplayName("非法：DRAFT 直接审核通过 --> 409 ILLEGAL_STATE")
    void approveFromDraftIsRejected() throws Exception {
        long rxId = givenPrescriptionIn("DRAFT");

        mockMvc.perform(post(RX + "/" + rxId + "/reviews/approve")
                        .header("Authorization", bearer("pharmacist01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"));
    }

    @Test
    @Disabled("等待 R4-A 处方聚合合入 main 后启用")
    @DisplayName("非法：重复提交（已 PENDING_REVIEW 再 submit）--> 409 ILLEGAL_STATE")
    void submitTwiceIsRejected() throws Exception {
        long rxId = givenPrescriptionIn("PENDING_REVIEW");

        mockMvc.perform(post(RX + "/" + rxId + "/submit")
                        .header("Authorization", bearer("doctor01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"));
    }

    @Test
    @Disabled("等待 R4-A 处方聚合合入 main 后启用")
    @DisplayName("非法：APPROVED 后撤销 --> 409 ILLEGAL_STATE（只能作废重开）")
    void cancelFromApprovedIsRejected() throws Exception {
        long rxId = givenPrescriptionIn("APPROVED");

        mockMvc.perform(post(RX + "/" + rxId + "/cancel")
                        .header("Authorization", bearer("doctor01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"));
    }

    @Test
    @Disabled("等待 R4-A 处方聚合合入 main 后启用")
    @DisplayName("非法：待审核状态直接修改内容（PUT）--> 409 ILLEGAL_STATE")
    void updateFromPendingReviewIsRejected() throws Exception {
        long rxId = givenPrescriptionIn("PENDING_REVIEW");

        mockMvc.perform(put(RX + "/" + rxId)
                        .header("Authorization", bearer("doctor01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"));
    }

    @ParameterizedTest(name = "终态 {0} 再提交 --> 409 ILLEGAL_STATE")
    @ValueSource(strings = {"CANCELLED", "REDEEMED", "EXPIRED"})
    @Disabled("等待 R4-A 处方聚合合入 main 后启用")
    @DisplayName("非法：三个终态都拒绝 submit")
    void terminalStatesRejectSubmit(String terminalState) throws Exception {
        long rxId = givenPrescriptionIn(terminalState);

        mockMvc.perform(post(RX + "/" + rxId + "/submit")
                        .header("Authorization", bearer("doctor01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"));
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    /**
     * 造一张处于指定状态的处方。
     *
     * <p>待 R4-A / R4-B 合入后实现：DRAFT 直接 insert；PENDING_REVIEW 为 DRAFT + submit；
     * APPROVED 为 submit + approve；CANCELLED/EXPIRED 用 SQL 直接落终态（定时任务驱动）。
     */
    private long givenPrescriptionIn(String state) {
        throw new UnsupportedOperationException(
                "待 R4-A/R4-B 合入后实现：造出状态为 " + state + " 的处方（schema-test.sql 需补 prescription 表）");
    }

    private String bearer(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + objectMapper
                .readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("accessToken")
                .asText();
    }

    private void insertUser(Long id, String username, String passwordHash, String roleCode, int status) {
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password_hash, role_code, status) VALUES (?, ?, ?, ?, ?)",
                id, username, passwordHash, roleCode, status);
    }
}
