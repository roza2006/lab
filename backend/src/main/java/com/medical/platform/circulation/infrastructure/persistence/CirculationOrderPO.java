package com.medical.platform.circulation.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 流转单持久化对象，列名与 {@code database/schema.sql} 的 {@code circulation_order} 一致。
 */
@TableName("circulation_order")
public class CirculationOrderPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String circulationNo;
    private Long rxId;
    private Long pharmacyId;
    private String status;
    private String idempotencyKey;
    private LocalDateTime circulatedAt;
    private LocalDateTime acceptedAt;
    private String rejectReason;
    private LocalDateTime redeemedAt;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCirculationNo() {
        return circulationNo;
    }

    public void setCirculationNo(String circulationNo) {
        this.circulationNo = circulationNo;
    }

    public Long getRxId() {
        return rxId;
    }

    public void setRxId(Long rxId) {
        this.rxId = rxId;
    }

    public Long getPharmacyId() {
        return pharmacyId;
    }

    public void setPharmacyId(Long pharmacyId) {
        this.pharmacyId = pharmacyId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public LocalDateTime getCirculatedAt() {
        return circulatedAt;
    }

    public void setCirculatedAt(LocalDateTime circulatedAt) {
        this.circulatedAt = circulatedAt;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public LocalDateTime getRedeemedAt() {
        return redeemedAt;
    }

    public void setRedeemedAt(LocalDateTime redeemedAt) {
        this.redeemedAt = redeemedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
