package com.xddcodec.fs.storage.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xddcodec.fs.framework.common.domain.Result;
import com.xddcodec.fs.log.constant.OperationType;
import com.xddcodec.fs.log.service.SysOperationLogService;
import com.xddcodec.fs.storage.domain.StoragePlatform;
import com.xddcodec.fs.storage.domain.StorageSetting;
import com.xddcodec.fs.storage.domain.cmd.StorageSettingAddCmd;
import com.xddcodec.fs.storage.domain.cmd.StorageSettingEditCmd;
import com.xddcodec.fs.storage.domain.vo.StorageActivePlatformsVO;
import com.xddcodec.fs.storage.domain.vo.StoragePlatformVO;
import com.xddcodec.fs.storage.domain.vo.StorageSettingUserVO;
import com.xddcodec.fs.storage.service.StoragePlatformService;
import com.xddcodec.fs.storage.service.StorageSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/apis/storage")
@RequiredArgsConstructor
@Tag(name = "存储平台管理")
public class StorageController {

    private final StoragePlatformService storagePlatformService;

    private final StorageSettingService storageSettingService;

    private final SysOperationLogService operationLogService;

    @Operation(summary = "获取存储平台列表")
    @GetMapping("/platforms")
    public Result<List<StoragePlatformVO>> getPlatforms() {
        List<StoragePlatformVO> result = storagePlatformService.getList();
        return Result.ok(result);
    }

    @Operation(summary = "获取用户存储平台配置列表")
    @GetMapping("/platform/settings")
    @SaCheckPermission("storage:manage")
    public Result<List<StorageSettingUserVO>> getStorageSettingsByUser() {
        List<StorageSettingUserVO> result = storageSettingService.getStorageSettingsByUser();
        return Result.ok(result);
    }

    @Operation(summary = "根据标识符获取存储平台详情")
    @GetMapping("/platform/{identifier}")
    public Result<StoragePlatform> getStoragePlatformByIdentifier(@PathVariable("identifier") String identifier) {
        StoragePlatform detail = storagePlatformService.getStoragePlatformByIdentifier(identifier);
        return Result.ok(detail);
    }

    @Operation(summary = "启用或禁用存储平台")
    @PostMapping("/settings/{id}/{action}")
    @SaCheckPermission("storage:manage")
    public Result<StorageSetting> enableOrDisableStoragePlatform(@PathVariable("id") String id, @PathVariable("action") Integer action) {
        storageSettingService.enableOrDisableStoragePlatform(id, action);
        operationLogService.recordSuccess(
                OperationType.SWITCH_STORAGE,
                action == 0 ? "禁用存储配置" : "启用存储配置",
                "STORAGE",
                id,
                id,
                null
        );
        return Result.ok();
    }

    @Operation(summary = "新增存储平台配置")
    @PostMapping("/settings")
    @SaCheckPermission("storage:manage")
    public Result<StorageSetting> saveOrUpdateStorageSetting(@Validated @RequestBody StorageSettingAddCmd cmd) {
        storageSettingService.addStorageSetting(cmd);
        operationLogService.recordSuccess(
                OperationType.ADD_STORAGE,
                "新增存储配置",
                "STORAGE",
                null,
                cmd.getRemark(),
                "存储类型: " + cmd.getPlatformIdentifier()
        );
        return Result.ok();
    }

    @Operation(summary = "编辑存储平台配置")
    @PutMapping("/settings")
    @SaCheckPermission("storage:manage")
    public Result<StorageSetting> saveOrUpdateStorageSetting(@Validated @RequestBody StorageSettingEditCmd cmd) {
        storageSettingService.editStorageSetting(cmd);
        operationLogService.recordSuccess(
                OperationType.UPDATE_STORAGE,
                "修改存储配置",
                "STORAGE",
                cmd.getSettingId(),
                cmd.getRemark(),
                null
        );
        return Result.ok();
    }

    @Operation(summary = "删除存储平台配置")
    @DeleteMapping("/settings/{id}")
    @SaCheckPermission("storage:manage")
    public Result<StorageSetting> saveOrUpdateStorageSetting(@PathVariable String id) {
        StorageSetting setting = storageSettingService.getById(id);
        storageSettingService.deleteStorageSettingById(id);
        operationLogService.recordSuccess(
                OperationType.DELETE_STORAGE,
                "删除存储配置",
                "STORAGE",
                id,
                setting == null ? id : setting.getRemark(),
                setting == null ? null : "存储类型: " + setting.getPlatformIdentifier()
        );
        return Result.ok();
    }

    @Operation(summary = "获取用户已启用存储平台列表")
    @GetMapping("/active-platforms")
    public Result<List<StorageActivePlatformsVO>> getActiveStoragePlatforms() {
        List<StorageActivePlatformsVO> settings = storageSettingService.getActiveStoragePlatforms();
        return Result.ok(settings);
    }
}
