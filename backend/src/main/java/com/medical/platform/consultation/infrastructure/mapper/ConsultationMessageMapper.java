package com.medical.platform.consultation.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.platform.consultation.infrastructure.persistence.ConsultationMessagePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ConsultationMessageMapper extends BaseMapper<ConsultationMessagePO> {

    @Select("SELECT * FROM consultation_message WHERE session_id = #{sessionId}"
            + " ORDER BY id ASC LIMIT #{size} OFFSET #{offset}")
    List<ConsultationMessagePO> selectPage(@Param("sessionId") Long sessionId,
                                           @Param("offset") int offset,
                                           @Param("size") int size);

    @Select("SELECT COUNT(*) FROM consultation_message WHERE session_id = #{sessionId}")
    long countBySession(@Param("sessionId") Long sessionId);
}
