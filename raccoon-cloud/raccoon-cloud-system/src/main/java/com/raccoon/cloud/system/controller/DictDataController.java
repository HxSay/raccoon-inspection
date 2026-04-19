package com.raccoon.cloud.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.raccoon.cloud.system.model.DictData;
import com.raccoon.cloud.system.model.dto.DictDataQueryRequest;
import com.raccoon.cloud.system.model.dto.DictDataRequest;
import com.raccoon.cloud.system.service.DictDataService;
import com.raccoon.common.result.HxResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "字典数据接口", description = "字典数据管理相关接口")
@RestController
@RequestMapping("/dict")
public class DictDataController {

    @Autowired
    private DictDataService dictDataService;

    @Operation(summary = "字典数据分页查询", description = "支持字典类型编码、显示名称、状态筛选")
    @PostMapping("/data/page")
    @PreAuthorize("hasRole('admin')")
    public HxResult<IPage<DictData>> pageData(@Validated @RequestBody DictDataQueryRequest query) {
        return HxResult.success(dictDataService.page(query));
    }

    @Operation(summary = "字典数据详情", description = "根据ID获取字典数据详情")
    @GetMapping("/data/{id}")
    @PreAuthorize("hasRole('admin')")
    public HxResult<DictData> getDataById(@PathVariable Long id) {
        return HxResult.success(dictDataService.getById(id));
    }

    @Operation(summary = "字典数据新增", description = "新增字典数据")
    @PostMapping("/data/add")
    @PreAuthorize("hasRole('admin')")
    public HxResult<?> addData(@Validated @RequestBody DictDataRequest request) {
        if (!dictDataService.checkValueUnique(request.getDictTypeCode(), request.getDictValue(), null)) {
            return HxResult.fail("字典存储值已存在");
        }
        if (!dictDataService.checkLabelUnique(request.getDictTypeCode(), request.getDictLabel(), null)) {
            return HxResult.fail("字典显示名称已存在");
        }
        dictDataService.add(request);
        return HxResult.success("字典数据新增成功");
    }

    @Operation(summary = "字典数据编辑", description = "编辑字典数据信息")
    @PostMapping("/data/update")
    @PreAuthorize("hasRole('admin')")
    public HxResult<?> updateData(@Validated @RequestBody DictDataRequest request) {
        if (!dictDataService.checkValueUnique(request.getDictTypeCode(), request.getDictValue(), request.getId())) {
            return HxResult.fail("字典存储值已存在");
        }
        if (!dictDataService.checkLabelUnique(request.getDictTypeCode(), request.getDictLabel(), request.getId())) {
            return HxResult.fail("字典显示名称已存在");
        }
        dictDataService.update(request);
        return HxResult.success("字典数据编辑成功");
    }

    @Operation(summary = "字典数据删除", description = "删除字典数据")
    @PostMapping("/data/delete")
    @PreAuthorize("hasRole('admin')")
    public HxResult<?> deleteData(@RequestParam Long id) {
        dictDataService.delete(id);
        return HxResult.success("字典数据删除成功");
    }

    @Operation(summary = "根据字典类型编码获取字典数据", description = "获取指定字典类型的所有字典数据")
    @GetMapping("/data/{dictTypeCode}")
    public HxResult<List<DictData>> getDataByTypeCode(@PathVariable String dictTypeCode) {
        return HxResult.success(dictDataService.getByTypeCode(dictTypeCode));
    }
}
