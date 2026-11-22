package com.medical.platform.consultation.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.platform.consultation.infrastructure.persistence.ConsultationSessionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 会话查询。分页统一用 LIMIT/OFFSET（不引入 MyBatis-Plus 分页插件，避免多一个全局拦截器）。
 */
@Mapper
public interface ConsultationSessionMapper extends BaseMapper<ConsultationSessionPO> {

    @Select("<script>"
            + "SELECT * FROM consultation_session WHERE patient_id = #{patientId}"
            + "<if test='status != null'> AND status = #{status}</if>"
            + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}"
            + "</script>")
    List<ConsultationSessionPO> selectPatientPage(@Param("patientId") Long patientId,
                                                  @Param("status") String status,
                                                  @Param("offset") int offset,
                                                  @Param("size") int size);

    @Select("<script>"
            + "SELECT COUNT(*) FROM consultation_session WHERE patient_id = #{patientId}"
            + "<if test='status != null'> AND status = #{status}</if>"
            + "</script>")
    long countPatient(@Param("patientId") Long patientId, @Param("status") String status);

    @Select("<script>"
            + "SELECT * FROM consultation_session"
            + " WHERE (doctor_id = #{doctorId} OR status = 'WAITING')"
            + "<if test='status != null'> AND status = #{status}</if>"
            + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}"
            + "</script>")
    List<ConsultationSessionPO> selectDoctorPage(@Param("doctorId") Long doctorId,
                                                 @Param("status") String status,
                                                 @Param("offset") int offset,
                                                 @Param("size") int size);

    @Select("<script>"
            + "SELECT COUNT(*) FROM consultation_session"
            + " WHERE (doctor_id = #{doctorId} OR status = 'WAITING')"
            + "<if test='status != null'> AND status = #{status}</if>"
            + "</script>")
    long countDoctor(@Param("doctorId") Long doctorId, @Param("status") String status);

    @Select("<script>"
            + "SELECT * FROM consultation_session"
            + "<if test='status != null'> WHERE status = #{status}</if>"
            + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}"
            + "</script>")
    List<ConsultationSessionPO> selectAllPage(@Param("status") String status,
                                              @Param("offset") int offset,
                                              @Param("size") int size);

    @Select("<script>"
            + "SELECT COUNT(*) FROM consultation_session"
            + "<if test='status != null'> WHERE status = #{status}</if>"
            + "</script>")
    long countAll(@Param("status") String status);
}
