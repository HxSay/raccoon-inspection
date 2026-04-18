package com.raccoon.cloud.system.controller;

import com.raccoon.cloud.system.model.Role;
import com.raccoon.cloud.system.model.dto.RoleRequest;
import com.raccoon.cloud.system.service.RoleService;
import com.raccoon.common.result.HxResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/role")
@Tag(name = "角色管理接口", description = "角色管理相关接口")
public class RoleController {

    @Autowired
    private RoleService roleService;

    @GetMapping("/list")
    @Operation(summary = "角色列表查询", description = "获取所有角色列表")
    public HxResult<List<Role>> list() {
        return HxResult.success(roleService.list());
    }

    @PostMapping("/add")
    @Operation(summary = "角色新增", description = "新增角色")
    public HxResult<?> add(@Valid @RequestBody RoleRequest request) {
        roleService.add(request);
        return HxResult.success("角色新增成功");
    }

    @PostMapping("/update")
    @Operation(summary = "角色编辑", description = "编辑角色信息")
    public HxResult<?> update(@Valid @RequestBody RoleRequest request) {
        roleService.update(request);
        return HxResult.success("角色编辑成功");
    }

    @PostMapping("/assign")
    @Operation(summary = "给用户分配角色", description = "为指定用户分配角色")
    public HxResult<?> assignRoles(@RequestParam Long userId, @RequestBody List<Long> roleIds) {
        roleService.assignRoles(userId, roleIds);
        return HxResult.success("角色分配成功");
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "获取用户角色", description = "根据用户ID获取角色列表")
    public HxResult<List<Role>> getRolesByUserId(@PathVariable Long userId) {
        return HxResult.success(roleService.getRolesByUserId(userId));
    }
}