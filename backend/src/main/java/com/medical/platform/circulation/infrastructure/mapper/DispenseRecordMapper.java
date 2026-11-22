package com.medical.platform.circulation.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.platform.circulation.infrastructure.persistence.DispenseRecordPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DispenseRecordMapper extends BaseMapper<DispenseRecordPO> {
}
