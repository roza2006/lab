package com.medical.platform.prescription.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 处方聚合的状态迁移与明细校验，不启动 Spring 上下文。
 */
class PrescriptionAggregateTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 10, 0);

    @Test
    @DisplayName("正向链路：草稿提交、审核通过、流转、核销")
    void forwardPathReachesRedeemed() {
        Prescription prescription = draft();

        prescription.submit(NOW.plusMinutes(1));
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.PENDING_REVIEW);
        assertThat(prescription.getVersion()).isEqualTo(2);

        prescription.approve(9L, "a".repeat(64), NOW.plusHours(1));
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.APPROVED);
        assertThat(prescription.getSignedHash()).hasSize(64);
        assertThat(prescription.getValidUntil()).isEqualTo(NOW.plusHours(73));

        prescription.circulate(NOW.plusHours(2));
        prescription.redeem(NOW.plusHours(3));
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.REDEEMED);
        assertThat(prescription.getVersion()).isEqualTo(5);
    }

    @Test
    @DisplayName("驳回后可退回草稿并重新提交")
    void rejectedDraftCanBeRevised() {
        Prescription prescription = draft();
        prescription.submit(NOW);
        prescription.reject(9L, "剂量超出成人常用范围，请调整后再提交", NOW.plusMinutes(10));

        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.REJECTED);
        assertThat(prescription.getRejectReason()).contains("剂量");

        prescription.revise(NOW.plusMinutes(20));
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.DRAFT);
        assertThat(prescription.getRejectReason()).isNull();

        prescription.submit(NOW.plusMinutes(30));
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("非法迁移：草稿不能直接审核通过")
    void draftCannotBeApproved() {
        Prescription prescription = draft();

        assertThatThrownBy(() -> prescription.approve(9L, "a".repeat(64), NOW))
                .isInstanceOf(PrescriptionException.class)
                .extracting(ex -> ((PrescriptionException) ex).error())
                .isEqualTo(PrescriptionError.RX_STATE_INVALID);
    }

    @Test
    @DisplayName("终态不可再撤销")
    void cancelledPrescriptionIsTerminal() {
        Prescription prescription = draft();
        prescription.cancel(NOW);

        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.CANCELLED);
        assertThatThrownBy(() -> prescription.cancel(NOW.plusMinutes(1)))
                .isInstanceOf(PrescriptionException.class)
                .extracting(ex -> ((PrescriptionException) ex).error())
                .isEqualTo(PrescriptionError.RX_STATE_INVALID);
    }

    @Test
    @DisplayName("未到失效时间不能置为 EXPIRED")
    void expireRequiresTimeout() {
        Prescription prescription = draft();
        prescription.submit(NOW);
        prescription.approve(9L, "a".repeat(64), NOW);

        assertThatThrownBy(() -> prescription.expire(NOW.plusHours(71)))
                .isInstanceOf(PrescriptionException.class)
                .extracting(ex -> ((PrescriptionException) ex).error())
                .isEqualTo(PrescriptionError.RX_STATE_INVALID);

        prescription.expire(NOW.plusHours(73));
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.EXPIRED);
    }

    @Test
    @DisplayName("同一药品不能重复添加")
    void duplicateDrugIsRejected() {
        PrescriptionItem item = item("DRUG001");

        assertThatThrownBy(() -> Prescription.draft(1L, 2L, 3L, "RX20261117000001",
                "2 型糖尿病", List.of(item, item), NOW))
                .isInstanceOf(PrescriptionException.class)
                .extracting(ex -> ((PrescriptionException) ex).error())
                .isEqualTo(PrescriptionError.RX_DUPLICATE_DRUG);
    }

    @Test
    @DisplayName("提交空明细返回 RX_ITEM_EMPTY")
    void emptyItemsCannotBeSubmitted() {
        Prescription prescription = draft();
        prescription.removeItem(prescription.getItems().get(0).id(), NOW);

        assertThatThrownBy(() -> prescription.submit(NOW.plusMinutes(1)))
                .isInstanceOf(PrescriptionException.class)
                .extracting(ex -> ((PrescriptionException) ex).error())
                .isEqualTo(PrescriptionError.RX_ITEM_EMPTY);
    }

    private Prescription draft() {
        return Prescription.draft(1L, 2L, 3L, "RX20261117000001",
                "2 型糖尿病", List.of(item("DRUG001"), item("DRUG002")), NOW);
    }

    private PrescriptionItem item(String drugCode) {
        return PrescriptionItem.create(drugCode, "盐酸二甲双胍片", "0.5g*20 片",
                "0.5g", "每日 2 次", "30 天", 2, new BigDecimal("15.00"), 0);
    }
}
