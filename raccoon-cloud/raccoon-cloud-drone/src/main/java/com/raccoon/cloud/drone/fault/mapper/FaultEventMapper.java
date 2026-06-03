package com.raccoon.cloud.drone.fault.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.raccoon.cloud.drone.fault.entity.FaultEventEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FaultEventMapper extends BaseMapper<FaultEventEntity> {
}
