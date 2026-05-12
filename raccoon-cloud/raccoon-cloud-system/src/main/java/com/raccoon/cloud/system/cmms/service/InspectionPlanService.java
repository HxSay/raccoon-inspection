package com.raccoon.cloud.system.cmms.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.system.cmms.dto.InspectionPlanPageRequest;
import com.raccoon.cloud.system.cmms.entity.InspectionPlan;
import com.raccoon.cloud.system.cmms.mapper.InspectionPlanMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class InspectionPlanService {

    private final InspectionPlanMapper mapper;
    private final ObjectMapper objectMapper;

    public InspectionPlanService(InspectionPlanMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public IPage<InspectionPlan> page(InspectionPlanPageRequest req) {
        QueryWrapper<InspectionPlan> w = new QueryWrapper<>();
        if (StringUtils.hasText(req.getPlanName())) {
            w.like("plan_name", req.getPlanName());
        }
        if (req.getStatus() != null) {
            w.eq("status", req.getStatus());
        }
        w.orderByDesc("id");
        Page<InspectionPlan> p = new Page<>(req.getPage(), req.getSize());
        return mapper.selectPage(p, w);
    }

    public void save(InspectionPlan row) {
        if (StringUtils.hasText(row.getDeviceIds())) {
            try {
                JsonNode node = objectMapper.readTree(row.getDeviceIds());
                if (!node.isArray()) {
                    throw new IllegalArgumentException("deviceIds 必须是 JSON 数组");
                }
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException("deviceIds JSON 格式错误: " + e.getMessage());
            }
        }
        if (row.getStatus() == null) {
            row.setStatus(1);
        }
        if (row.getId() == null) {
            mapper.insert(row);
        } else {
            mapper.updateById(row);
        }
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }

    public InspectionPlan get(Long id) {
        return mapper.selectById(id);
    }
}
