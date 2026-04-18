package com.raccoon.cloud.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.raccoon.cloud.system.model.User;
import com.raccoon.cloud.system.model.dto.UserQueryRequest;
import com.raccoon.cloud.system.model.dto.UserRequest;
import com.raccoon.cloud.system.service.UserService;
import com.raccoon.common.result.HxResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/user")
@Tag(name = "用户管理接口", description = "用户管理相关接口")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/page")
    @Operation(summary = "用户分页查询", description = "支持用户名、手机号、状态筛选")
    public HxResult<IPage<User>> page(@Valid @RequestBody UserQueryRequest query) {
        return HxResult.success(userService.page(query));
    }

    @PostMapping("/add")
    @Operation(summary = "用户新增", description = "新增用户")
    public HxResult<?> add(@Valid @RequestBody UserRequest request) {
        userService.add(request);
        return HxResult.success("用户新增成功");
    }

    @PostMapping("/update")
    @Operation(summary = "用户编辑", description = "编辑用户信息")
    public HxResult<?> update(@Valid @RequestBody UserRequest request) {
        userService.update(request);
        return HxResult.success("用户编辑成功");
    }

    @PostMapping("/status")
    @Operation(summary = "用户状态修改", description = "启用/禁用用户")
    public HxResult<?> updateStatus(@RequestParam Long id, @RequestParam Integer status) {
        userService.updateStatus(id, status);
        return HxResult.success("状态修改成功");
    }

    @PostMapping("/resetPassword")
    @Operation(summary = "重置密码", description = "重置用户密码为123456")
    public HxResult<?> resetPassword(@RequestParam Long id) {
        userService.resetPassword(id);
        return HxResult.success("密码重置成功");
    }

    @PostMapping("/delete")
    @Operation(summary = "删除用户", description = "软删除用户")
    public HxResult<?> delete(@RequestParam Long id) {
        userService.delete(id);
        return HxResult.success("用户删除成功");
    }
}