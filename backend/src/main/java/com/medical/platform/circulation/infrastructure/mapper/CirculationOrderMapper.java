package com.medical.platform.circulation.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.platform.circulation.infrastructure.persistence.CirculationOrderPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CirculationOrderMapper extends BaseMapper<CirculationOrderPO> {

    /**
     * 药师所属药房。没有档案返回 null（调用方按 403 处理），
     * 避免把“未绑定药房”误判为“可以跨药房核销”。
     */
    @Select("SELECT pharmacy_id FROM pharmacist_profile WHERE user_id = #{userId} LIMIT 1")
    Long selectPharmacistPharmacyId(@Param("userId") Long userId);

    @Select("<script>"
            + "SELECT o.* FROM circulation_order o JOIN prescription p ON p.id = o.rx_id"
            + " WHERE p.patient_id = #{patientId}"
            + "<if test='status != null'> AND o.status = #{status}</if>"
            + " ORDER BY o.id DESC LIMIT #{size} OFFSET #{offset}"
            + "</script>")
    List<CirculationOrderPO> selectPatientPage(@Param("patientId") Long patientId,
                                               @Param("status") String status,
                                               @Param("offset") int offset,
                                               @Param("size") int size);

    @Select("<script>"
            + "SELECT COUNT(*) FROM circulation_order o JOIN prescription p ON p.id = o.rx_id"
            + " WHERE p.patient_id = #{patientId}"
            + "<if test='status != null'> AND o.status = #{status}</if>"
            + "</script>")
    long countPatient(@Param("patientId") Long patientId, @Param("status") String status);

    @Select("<script>"
            + "SELECT o.* FROM circulation_order o JOIN prescription p ON p.id = o.rx_id"
            + " WHERE p.doctor_id = #{doctorId}"
            + "<if test='status != null'> AND o.status = #{status}</if>"
            + " ORDER BY o.id DESC LIMIT #{size} OFFSET #{offset}"
            + "</script>")
    List<CirculationOrderPO> selectDoctorPage(@Param("doctorId") Long doctorId,
                                              @Param("status") String status,
                                              @Param("offset") int offset,
                                              @Param("size") int size);

    @Select("<script>"
            + "SELECT COUNT(*) FROM circulation_order o JOIN prescription p ON p.id = o.rx_id"
            + " WHERE p.doctor_id = #{doctorId}"
            + "<if test='status != null'> AND o.status = #{status}</if>"
            + "</script>")
    long countDoctor(@Param("doctorId") Long doctorId, @Param("status") String status);

    @Select("<script>"
            + "SELECT * FROM circulation_order WHERE pharmacy_id = #{pharmacyId}"
            + "<if test='status != null'> AND status = #{status}</if>"
            + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}"
            + "</script>")
    List<CirculationOrderPO> selectPharmacyPage(@Param("pharmacyId") Long pharmacyId,
                                                @Param("status") String status,
                                                @Param("offset") int offset,
                                                @Param("size") int size);

    @Select("<script>"
            + "SELECT COUNT(*) FROM circulation_order WHERE pharmacy_id = #{pharmacyId}"
            + "<if test='status != null'> AND status = #{status}</if>"
            + "</script>")
    long countPharmacy(@Param("pharmacyId") Long pharmacyId, @Param("status") String status);

    @Select("<script>"
            + "SELECT * FROM circulation_order"
            + "<if test='status != null'> WHERE status = #{status}</if>"
            + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}"
            + "</script>")
    List<CirculationOrderPO> selectAllPage(@Param("status") String status,
                                           @Param("offset") int offset,
                                           @Param("size") int size);

    @Select("<script>"
            + "SELECT COUNT(*) FROM circulation_order"
            + "<if test='status != null'> WHERE status = #{status}</if>"
            + "</script>")
    long countAll(@Param("status") String status);
}
