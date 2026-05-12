package com.raccoon.cloud.system.cmms.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.raccoon.cloud.system.cmms.dto.WorkOrderPageRequest;
import com.raccoon.cloud.system.cmms.entity.WorkOrder;
import com.raccoon.cloud.system.cmms.mapper.WorkOrderMapper;
import com.raccoon.cloud.system.model.User;
import com.raccoon.cloud.system.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class WorkOrderService {

    private final WorkOrderMapper mapper;
    private final UserService userService;

    public WorkOrderService(WorkOrderMapper mapper, UserService userService) {
        this.mapper = mapper;
        this.userService = userService;
    }

    public IPage<WorkOrder> page(WorkOrderPageRequest req) {
        QueryWrapper<WorkOrder> w = new QueryWrapper<>();
        if (StringUtils.hasText(req.getOrderCode())) {
            w.like("order_code", req.getOrderCode());
        }
        if (req.getStatus() != null) {
            w.eq("status", req.getStatus());
        }
        if (req.getDeviceId() != null) {
            w.eq("device_id", req.getDeviceId());
        }
        if (req.getAssignUserId() != null) {
            w.eq("assign_user_id", req.getAssignUserId());
        }
        if (req.getReportUserId() != null) {
            w.eq("report_user_id", req.getReportUserId());
        }
        w.orderByDesc("report_time", "id");
        Page<WorkOrder> p = new Page<>(req.getPage(), req.getSize());
        return mapper.selectPage(p, w);
    }

    public void save(WorkOrder row) {
        if (row.getStatus() == null) {
            row.setStatus(0);
        }
        if (row.getLevel() == null) {
            row.setLevel(2);
        }
        if (!StringUtils.hasText(row.getOrderCode())) {
            row.setOrderCode(genOrderCode());
        }
        if (row.getReportUserId() == null) {
            User u = userService.getCurrentUser();
            row.setReportUserId(u.getId());
        }
        if (row.getReportTime() == null) {
            row.setReportTime(LocalDateTime.now());
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

    /**
     * 巡检异常自动建单：source=1 巡检上报，order_type=1 故障维修。
     */
    public WorkOrder createFromInspection(Long deviceId, Long reportUserId, String faultDescription, String faultImageUrls) {
        WorkOrder wo = new WorkOrder();
        wo.setDeviceId(deviceId);
        wo.setSource(1);
        wo.setOrderType(1);
        wo.setFaultDescription(faultDescription);
        wo.setFaultImageUrls(faultImageUrls);
        wo.setReportUserId(reportUserId);
        wo.setStatus(0);
        wo.setLevel(2);
        save(wo);
        return wo;
    }

    public WorkOrder get(Long id) {
        return mapper.selectById(id);
    }

    public void updateStatus(Long id, Integer status) {
        WorkOrder w = mapper.selectById(id);
        if (w == null) {
            throw new IllegalArgumentException("工单不存在");
        }
        w.setStatus(status);
        if (status != null && status == 3) {
            w.setActualFinishTime(LocalDateTime.now());
        }
        mapper.updateById(w);
    }

    private String genOrderCode() {
        String ts = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        int r = (int) (Math.random() * 9000) + 1000;
        return "WO" + ts + r;
    }
}
