package com.xddcodec.fs.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xddcodec.fs.framework.common.context.WorkspaceContext;
import com.xddcodec.fs.framework.common.domain.Result;
import com.xddcodec.fs.log.constant.OperationType;
import com.xddcodec.fs.log.service.SysOperationLogService;
import com.xddcodec.fs.system.domain.dto.CreateCustomRoleCmd;
import com.xddcodec.fs.system.domain.dto.UpdateCustomRoleCmd;
import com.xddcodec.fs.system.domain.vo.SysRoleVO;
import com.xddcodec.fs.system.service.SysRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/apis/role")
@RequiredArgsConstructor
@Tag(name = "角色管理")
public class RoleController {

    private final SysRoleService sysRoleService;
    private final SysOperationLogService operationLogService;

    @Operation(summary = "根据工作区获取角色列表")
    @GetMapping("/list")
    @SaCheckPermission("member:manage")
    public Result<List<SysRoleVO>> getListByWorkspace() {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        List<SysRoleVO> roles = sysRoleService.getListByWorkspace(workspaceId);
        return Result.ok(roles);
    }

    @Operation(summary = "角色详情（含权限列表）")
    @GetMapping("/{id}")
    @SaCheckPermission("member:manage")
    public Result<SysRoleVO> getDetail(@PathVariable Long id) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        return Result.ok(sysRoleService.getRoleDetail(workspaceId, id));
    }

    @Operation(summary = "创建自定义角色")
    @PostMapping
    @SaCheckPermission("member:manage")
    public Result<SysRoleVO> createCustom(@Valid @RequestBody CreateCustomRoleCmd cmd) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        SysRoleVO role = sysRoleService.createCustomRole(workspaceId, cmd);
        operationLogService.recordSuccess(
                OperationType.CREATE_ROLE,
                "创建角色",
                "ROLE",
                String.valueOf(role.getId()),
                role.getRoleName(),
                "权限数: " + cmd.getPermissions().size()
        );
        return Result.ok(role);
    }

    @Operation(summary = "更新自定义角色")
    @PutMapping("/{id}")
    @SaCheckPermission("member:manage")
    public Result<SysRoleVO> updateCustom(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCustomRoleCmd cmd) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        SysRoleVO role = sysRoleService.updateCustomRole(workspaceId, id, cmd);
        operationLogService.recordSuccess(
                OperationType.UPDATE_ROLE,
                "更新角色",
                "ROLE",
                String.valueOf(id),
                role.getRoleName(),
                "权限数: " + cmd.getPermissions().size()
        );
        return Result.ok(role);
    }

    @Operation(summary = "删除自定义角色")
    @DeleteMapping("/{id}")
    @SaCheckPermission("member:manage")
    public Result<Void> deleteCustom(@PathVariable Long id) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        SysRoleVO role = sysRoleService.getRoleDetail(workspaceId, id);
        sysRoleService.deleteCustomRole(workspaceId, id);
        operationLogService.recordSuccess(
                OperationType.DELETE_ROLE,
                "删除角色",
                "ROLE",
                String.valueOf(id),
                role.getRoleName(),
                null
        );
        return Result.ok();
    }
}
