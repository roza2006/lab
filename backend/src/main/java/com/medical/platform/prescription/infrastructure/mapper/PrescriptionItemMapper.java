package com.medical.platform.prescription.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.platform.prescription.infrastructure.persistence.PrescriptionItemPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PrescriptionItemMapper extends BaseMapper<PrescriptionItemPO> {
}
