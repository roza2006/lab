package com.medical.platform.circulation.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.platform.circulation.infrastructure.persistence.CirculationOrderPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CirculationOrderMapper extends BaseMapper<CirculationOrderPO> {

    /**
     * 药师所属药房。没有档案返回 null（调用方按 403 处理），
     * 避免把“未绑定药房”误判为“可以跨药房核销”。
     */
    @Select("SELECT pharmacy_id FROM pharmacist_profile WHERE user_id = #{userId} LIMIT 1")
    Long selectPharmacistPharmacyId(@Param("userId") Long userId);
}
