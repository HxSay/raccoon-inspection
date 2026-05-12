package com.raccoon.cloud.system.cmms.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.raccoon.cloud.system.cmms.entity.InspectionRecord;
import com.raccoon.cloud.system.cmms.mapper.InspectionRecordMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InspectionRecordService {

    private final InspectionRecordMapper mapper;

    public InspectionRecordService(InspectionRecordMapper mapper) {
        this.mapper = mapper;
    }

    public List<InspectionRecord> listByTask(Long taskId) {
        return mapper.selectList(new QueryWrapper<InspectionRecord>()
                .eq("task_id", taskId)
                .orderByAsc("id"));
    }
}
