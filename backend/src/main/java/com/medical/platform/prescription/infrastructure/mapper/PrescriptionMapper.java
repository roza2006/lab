package com.medical.platform.prescription.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.platform.prescription.infrastructure.persistence.PrescriptionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PrescriptionMapper extends BaseMapper<PrescriptionPO> {

    @Select("<script>"
            + "SELECT * FROM prescription WHERE patient_id = #{patientId}"
            + "<if test='status != null'> AND status = #{status}</if>"
            + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}"
            + "</script>")
    List<PrescriptionPO> selectPatientPage(@Param("patientId") Long patientId,
                                           @Param("status") String status,
                                           @Param("offset") int offset,
                                           @Param("size") int size);

    @Select("<script>"
            + "SELECT COUNT(*) FROM prescription WHERE patient_id = #{patientId}"
            + "<if test='status != null'> AND status = #{status}</if>"
            + "</script>")
    long countPatient(@Param("patientId") Long patientId, @Param("status") String status);

    @Select("<script>"
            + "SELECT * FROM prescription WHERE doctor_id = #{doctorId}"
            + "<if test='status != null'> AND status = #{status}</if>"
            + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}"
            + "</script>")
    List<PrescriptionPO> selectDoctorPage(@Param("doctorId") Long doctorId,
                                          @Param("status") String status,
                                          @Param("offset") int offset,
                                          @Param("size") int size);

    @Select("<script>"
            + "SELECT COUNT(*) FROM prescription WHERE doctor_id = #{doctorId}"
            + "<if test='status != null'> AND status = #{status}</if>"
            + "</script>")
    long countDoctor(@Param("doctorId") Long doctorId, @Param("status") String status);

    @Select("<script>"
            + "SELECT * FROM prescription"
            + "<if test='status != null'> WHERE status = #{status}</if>"
            + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}"
            + "</script>")
    List<PrescriptionPO> selectAllPage(@Param("status") String status,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    @Select("<script>"
            + "SELECT COUNT(*) FROM prescription"
            + "<if test='status != null'> WHERE status = #{status}</if>"
            + "</script>")
    long countAll(@Param("status") String status);
}
