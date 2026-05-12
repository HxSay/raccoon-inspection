package com.raccoon.cloud.system.cmms.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.raccoon.cloud.system.cmms.dto.CmmsPageRequest;
import com.raccoon.cloud.system.cmms.dto.DeviceInfoPageRequest;
import com.raccoon.cloud.system.cmms.dto.InspectionDeptPageRequest;
import com.raccoon.cloud.system.cmms.dto.SupplierPageRequest;
import com.raccoon.cloud.system.cmms.entity.*;
import com.raccoon.cloud.system.cmms.service.*;
import com.raccoon.common.result.HxResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备台账：分类、供应商、部门、仓库、设备主数据、巡检点。
 */
@RestController
@RequestMapping("/cmms/device")
@Tag(name = "CMMS-设备管理", description = "设备台账与主数据")
public class CmmsDeviceController {

    private final DeviceCategoryService deviceCategoryService;
    private final SupplierService supplierService;
    private final InspectionDeptService inspectionDeptService;
    private final InspectionWarehouseService inspectionWarehouseService;
    private final DeviceInfoService deviceInfoService;
    private final InspectionPointService inspectionPointService;

    public CmmsDeviceController(DeviceCategoryService deviceCategoryService,
                              SupplierService supplierService,
                              InspectionDeptService inspectionDeptService,
                              InspectionWarehouseService inspectionWarehouseService,
                              DeviceInfoService deviceInfoService,
                              InspectionPointService inspectionPointService) {
        this.deviceCategoryService = deviceCategoryService;
        this.supplierService = supplierService;
        this.inspectionDeptService = inspectionDeptService;
        this.inspectionWarehouseService = inspectionWarehouseService;
        this.deviceInfoService = deviceInfoService;
        this.inspectionPointService = inspectionPointService;
    }

    // --- 设备分类 ---
    @PostMapping("/category/list")
    @Operation(summary = "设备分类列表")
    public HxResult<List<DeviceCategory>> categoryList() {
        return HxResult.success(deviceCategoryService.listAll());
    }

    @PostMapping("/category/save")
    @Operation(summary = "保存设备分类")
    public HxResult<?> categorySave(@RequestBody DeviceCategory body) {
        deviceCategoryService.save(body);
        return HxResult.success("保存成功");
    }

    @PostMapping("/category/delete")
    @Operation(summary = "删除设备分类")
    public HxResult<?> categoryDelete(@RequestParam Long id) {
        deviceCategoryService.delete(id);
        return HxResult.success("删除成功");
    }

    // --- 供应商 ---
    @PostMapping("/supplier/page")
    @Operation(summary = "供应商分页")
    public HxResult<IPage<Supplier>> supplierPage(@RequestBody SupplierPageRequest req) {
        return HxResult.success(supplierService.page(req));
    }

    @PostMapping("/supplier/save")
    public HxResult<?> supplierSave(@RequestBody Supplier body) {
        supplierService.save(body);
        return HxResult.success("保存成功");
    }

    @PostMapping("/supplier/delete")
    public HxResult<?> supplierDelete(@RequestParam Long id) {
        supplierService.delete(id);
        return HxResult.success("删除成功");
    }

    // --- 部门 ---
    @PostMapping("/dept/page")
    public HxResult<IPage<InspectionDept>> deptPage(@RequestBody InspectionDeptPageRequest req) {
        return HxResult.success(inspectionDeptService.page(req));
    }

    @PostMapping("/dept/save")
    public HxResult<?> deptSave(@RequestBody InspectionDept body) {
        inspectionDeptService.save(body);
        return HxResult.success("保存成功");
    }

    @PostMapping("/dept/delete")
    public HxResult<?> deptDelete(@RequestParam Long id) {
        inspectionDeptService.delete(id);
        return HxResult.success("删除成功");
    }

    // --- 仓库 ---
    @PostMapping("/warehouse/page")
    public HxResult<IPage<InspectionWarehouse>> warehousePage(@RequestBody CmmsPageRequest req) {
        return HxResult.success(inspectionWarehouseService.page(req));
    }

    @PostMapping("/warehouse/save")
    public HxResult<?> warehouseSave(@RequestBody InspectionWarehouse body) {
        inspectionWarehouseService.save(body);
        return HxResult.success("保存成功");
    }

    @PostMapping("/warehouse/delete")
    public HxResult<?> warehouseDelete(@RequestParam Long id) {
        inspectionWarehouseService.delete(id);
        return HxResult.success("删除成功");
    }

    // --- 设备主表 ---
    @PostMapping("/info/page")
    public HxResult<IPage<DeviceInfo>> devicePage(@RequestBody DeviceInfoPageRequest req) {
        return HxResult.success(deviceInfoService.page(req));
    }

    @GetMapping("/info/get")
    public HxResult<DeviceInfo> deviceGet(@RequestParam Long id) {
        return HxResult.success(deviceInfoService.get(id));
    }

    @GetMapping("/info/byCode")
    @Operation(summary = "按设备编号查询", description = "扫码后核对 device_info.device_code")
    public HxResult<DeviceInfo> deviceByCode(@RequestParam String deviceCode) {
        DeviceInfo d = deviceInfoService.getByDeviceCode(deviceCode);
        return HxResult.success(d);
    }

    @PostMapping("/info/save")
    public HxResult<?> deviceSave(@RequestBody DeviceInfo body) {
        deviceInfoService.save(body);
        return HxResult.success("保存成功");
    }

    @PostMapping("/info/delete")
    public HxResult<?> deviceDelete(@RequestParam Long id) {
        deviceInfoService.delete(id);
        return HxResult.success("删除成功");
    }

    // --- 巡检点 ---
    @GetMapping("/point/list")
    public HxResult<List<InspectionPoint>> pointList(@RequestParam Long deviceId) {
        return HxResult.success(inspectionPointService.listByDevice(deviceId));
    }

    @PostMapping("/point/save")
    public HxResult<?> pointSave(@RequestBody InspectionPoint body) {
        inspectionPointService.save(body);
        return HxResult.success("保存成功");
    }

    @PostMapping("/point/delete")
    public HxResult<?> pointDelete(@RequestParam Long id) {
        inspectionPointService.delete(id);
        return HxResult.success("删除成功");
    }
}
