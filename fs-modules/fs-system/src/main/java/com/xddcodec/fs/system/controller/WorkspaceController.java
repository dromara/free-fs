package com.xddcodec.fs.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.mybatisflex.core.paginate.Page;
import com.xddcodec.fs.framework.common.context.WorkspaceContext;
import com.xddcodec.fs.framework.common.domain.Result;
import com.xddcodec.fs.log.constant.OperationType;
import com.xddcodec.fs.log.service.SysOperationLogService;
import com.xddcodec.fs.system.domain.dto.CreateInvitationCmd;
import com.xddcodec.fs.system.domain.dto.CreateWorkspaceCmd;
import com.xddcodec.fs.system.domain.dto.UpdateMemberRoleCmd;
import com.xddcodec.fs.system.domain.dto.UpdateWorkspaceCmd;
import com.xddcodec.fs.system.domain.vo.WorkspaceDetailVO;
import com.xddcodec.fs.system.domain.vo.WorkspaceInvitationVO;
import com.xddcodec.fs.system.domain.vo.WorkspaceMemberVO;
import com.xddcodec.fs.system.domain.vo.WorkspaceVO;
import com.xddcodec.fs.system.service.SysWorkspaceInvitationService;
import com.xddcodec.fs.system.service.SysWorkspaceMemberService;
import com.xddcodec.fs.system.service.SysWorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工作空间管理控制器
 *
 * @Author: xddcode
 * @Date: 2026/3/31
 */
@Validated
@RestController
@RequestMapping("/apis/workspace")
@RequiredArgsConstructor
@Tag(name = "工作空间管理")
public class WorkspaceController {

    private final SysWorkspaceService workspaceService;
    private final SysWorkspaceMemberService memberService;
    private final SysWorkspaceInvitationService invitationService;
    private final SysOperationLogService operationLogService;

    @Operation(summary = "获取当前用户工作空间列表")
    @GetMapping("/list")
    public Result<List<WorkspaceVO>> getWorkspacesByUser() {
        String userId = StpUtil.getLoginIdAsString();
        List<WorkspaceVO> results = workspaceService.getWorkspacesByUser(userId);
        return Result.ok(results);
    }

    @Operation(summary = "创建工作空间")
    @PostMapping
    public Result<WorkspaceVO> create(@Valid @RequestBody CreateWorkspaceCmd cmd) {
        WorkspaceVO result = workspaceService.createWorkspace(cmd);
        operationLogService.recordSuccess(
                result.getId(),
                OperationType.CREATE_WORKSPACE,
                "创建工作空间",
                "WORKSPACE",
                result.getId(),
                result.getName(),
                "Slug: " + result.getSlug()
        );
        return Result.ok(result);
    }

    @Operation(summary = "获取当前工作空间详情")
    @GetMapping("/current")
    public Result<WorkspaceDetailVO> getCurrent() {
        String wsId = WorkspaceContext.getWorkspaceId();
        String userId = StpUtil.getLoginIdAsString();
        WorkspaceDetailVO result = workspaceService.getCurrentDetail(wsId, userId);
        return Result.ok(result);
    }

    @Operation(summary = "更新工作空间信息")
    @PutMapping
    @SaCheckRole("admin")
    public Result<WorkspaceVO> update(@Valid @RequestBody UpdateWorkspaceCmd cmd) {
        String wsId = WorkspaceContext.getWorkspaceId();
        WorkspaceVO result = workspaceService.updateWorkspace(wsId, cmd);
        operationLogService.recordSuccess(
                OperationType.UPDATE_WORKSPACE,
                "更新工作空间",
                "WORKSPACE",
                wsId,
                result.getName(),
                null
        );
        return Result.ok(result);
    }

    @Operation(summary = "删除工作空间")
    @DeleteMapping
    public Result<Void> delete() {
        String wsId = WorkspaceContext.getWorkspaceId();
        String userId = StpUtil.getLoginIdAsString();
        WorkspaceDetailVO workspace = workspaceService.getCurrentDetail(wsId, userId);
        workspaceService.deleteWorkspace(wsId, userId);
        operationLogService.recordSuccess(
                wsId,
                OperationType.DELETE_WORKSPACE,
                "删除工作空间",
                "WORKSPACE",
                wsId,
                workspace.getName(),
                null
        );
        return Result.ok();
    }

    @Operation(summary = "检查 slug 可用性")
    @GetMapping("/check-slug")
    public Result<Boolean> checkSlug(@RequestParam String slug) {
        boolean available = workspaceService.checkSlug(slug);
        return Result.ok(available);
    }

    // ---- 成员管理 ----

    @Operation(summary = "分页查询工作空间成员")
    @GetMapping("/members")
    public Result<Page<WorkspaceMemberVO>> getMembers(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "20") int pageSize) {
        String wsId = WorkspaceContext.getWorkspaceId();
        Page<WorkspaceMemberVO> result = memberService.getMembers(wsId, pageNumber, pageSize);
        return Result.ok(result);
    }

    @Operation(summary = "更新成员角色")
    @PutMapping("/members/{userId}/role")
    @SaCheckPermission("member:manage")
    public Result<Void> updateMemberRole(
            @PathVariable String userId,
            @Valid @RequestBody UpdateMemberRoleCmd cmd) {
        String wsId = WorkspaceContext.getWorkspaceId();
        String currentUserId = StpUtil.getLoginIdAsString();
        memberService.updateMemberRole(wsId, userId, cmd.getRoleId(), currentUserId);
        operationLogService.recordSuccess(
                OperationType.UPDATE_MEMBER_ROLE,
                "修改成员角色",
                "MEMBER",
                userId,
                userId,
                "角色 ID: " + cmd.getRoleId()
        );
        return Result.ok();
    }

    @Operation(summary = "移除成员")
    @DeleteMapping("/members/{userId}")
    @SaCheckPermission("member:manage")
    public Result<Void> removeMember(@PathVariable String userId) {
        String wsId = WorkspaceContext.getWorkspaceId();
        String currentUserId = StpUtil.getLoginIdAsString();
        memberService.removeMember(wsId, userId, currentUserId);
        operationLogService.recordSuccess(
                OperationType.REMOVE_MEMBER,
                "移除成员",
                "MEMBER",
                userId,
                userId,
                null
        );
        return Result.ok();
    }

    // ---- 邀请管理 ----

    @Operation(summary = "获取邀请列表")
    @GetMapping("/invitations")
    public Result<List<WorkspaceInvitationVO>> getInvitations() {
        String wsId = WorkspaceContext.getWorkspaceId();
        List<WorkspaceInvitationVO> result = invitationService.getInvitations(wsId);
        return Result.ok(result);
    }

    @Operation(summary = "创建邀请")
    @PostMapping("/invitations")
    @SaCheckPermission("member:manage")
    public Result<WorkspaceInvitationVO> createInvitation(@Valid @RequestBody CreateInvitationCmd cmd) {
        String wsId = WorkspaceContext.getWorkspaceId();
        String inviterId = StpUtil.getLoginIdAsString();
        WorkspaceInvitationVO result = invitationService.createInvitation(wsId, cmd, inviterId);
        operationLogService.recordSuccess(
                OperationType.CREATE_INVITATION,
                "邀请成员",
                "INVITATION",
                result.getId(),
                result.getEmail(),
                "角色 ID: " + cmd.getRoleId()
        );
        return Result.ok(result);
    }

    @Operation(summary = "取消邀请")
    @DeleteMapping("/invitations/{id}")
    @SaCheckPermission("member:manage")
    public Result<Void> cancelInvitation(@PathVariable String id) {
        String wsId = WorkspaceContext.getWorkspaceId();
        invitationService.cancelInvitation(id, wsId);
        operationLogService.recordSuccess(
                OperationType.CANCEL_INVITATION,
                "取消邀请",
                "INVITATION",
                id,
                null,
                null
        );
        return Result.ok();
    }
}
