package com.medical.platform.prescription.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.platform.prescription.infrastructure.persistence.PrescriptionReviewPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PrescriptionReviewMapper extends BaseMapper<PrescriptionReviewPO> {
}
