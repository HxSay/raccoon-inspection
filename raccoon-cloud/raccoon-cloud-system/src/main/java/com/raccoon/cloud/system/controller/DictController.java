package com.raccoon.cloud.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.raccoon.cloud.system.model.DictType;
import com.raccoon.cloud.system.model.dto.DictTypeQueryRequest;
import com.raccoon.cloud.system.model.dto.DictTypeRequest;
import com.raccoon.cloud.system.service.DictDataService;
import com.raccoon.cloud.system.service.DictTypeService;
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
@Tag(name = "字典管理接口", description = "字典类型和数据管理相关接口")
@RestController
@RequestMapping("/dict")
public class DictController {

    @Autowired
    private DictTypeService dictTypeService;

    @Autowired
    private DictDataService dictDataService;

    @Operation(summary = "字典类型分页查询", description = "支持字典类型编码、名称、状态筛选")
    @PostMapping("/type/page")
    @PreAuthorize("hasRole('admin')")
    public HxResult<IPage<DictType>> pageType(@Validated @RequestBody DictTypeQueryRequest query) {
        return HxResult.success(dictTypeService.page(query));
    }

    @Operation(summary = "字典类型详情", description = "根据ID获取字典类型详情")
    @GetMapping("/type/{id}")
    @PreAuthorize("hasRole('admin')")
    public HxResult<DictType> getTypeById(@PathVariable Long id) {
        return HxResult.success(dictTypeService.getById(id));
    }

    @Operation(summary = "字典类型新增", description = "新增字典类型")
    @PostMapping("/type/add")
    @PreAuthorize("hasRole('admin')")
    public HxResult<?> addType(@Validated @RequestBody DictTypeRequest request) {
        if (!dictTypeService.checkCodeUnique(request.getDictTypeCode(), null)) {
            return HxResult.fail("字典类型编码已存在");
        }
        dictTypeService.add(request);
        return HxResult.success("字典类型新增成功");
    }

    @Operation(summary = "字典类型编辑", description = "编辑字典类型信息")
    @PostMapping("/type/update")
    @PreAuthorize("hasRole('admin')")
    public HxResult<?> updateType(@Validated @RequestBody DictTypeRequest request) {
        if (!dictTypeService.checkCodeUnique(request.getDictTypeCode(), request.getId())) {
            return HxResult.fail("字典类型编码已存在");
        }
        dictTypeService.update(request);
        return HxResult.success("字典类型编辑成功");
    }

    @Operation(summary = "字典类型删除", description = "删除字典类型")
    @PostMapping("/type/delete")
    @PreAuthorize("hasRole('admin')")
    public HxResult<?> deleteType(@RequestParam Long id) {
        DictType dictType = dictTypeService.getById(id);
        if (dictType == null) {
            return HxResult.fail("字典类型不存在");
        }
        if (dictDataService.countByTypeCode(dictType.getDictTypeCode()) > 0) {
            return HxResult.fail("该字典类型下存在字典数据，无法删除");
        }
        dictTypeService.delete(id);
        return HxResult.success();
    }

    @Operation(summary = "字典类型列表", description = "获取所有启用的字典类型列表")
    @GetMapping("/type/list")
    public HxResult<List<DictType>> listType() {
        return HxResult.success(dictTypeService.listAll());
    }
}
