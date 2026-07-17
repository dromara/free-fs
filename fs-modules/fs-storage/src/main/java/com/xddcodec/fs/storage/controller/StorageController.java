package com.xddcodec.fs.storage.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xddcodec.fs.framework.common.domain.Result;
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
        return Result.ok();
    }

    @Operation(summary = "新增存储平台配置")
    @PostMapping("/settings")
    @SaCheckPermission("storage:manage")
    public Result<StorageSetting> saveOrUpdateStorageSetting(@Validated @RequestBody StorageSettingAddCmd cmd) {
        storageSettingService.addStorageSetting(cmd);
        return Result.ok();
    }

    @Operation(summary = "编辑存储平台配置")
    @PutMapping("/settings")
    @SaCheckPermission("storage:manage")
    public Result<StorageSetting> saveOrUpdateStorageSetting(@Validated @RequestBody StorageSettingEditCmd cmd) {
        storageSettingService.editStorageSetting(cmd);
        return Result.ok();
    }

    @Operation(summary = "删除存储平台配置")
    @DeleteMapping("/settings/{id}")
    @SaCheckPermission("storage:manage")
    public Result<StorageSetting> saveOrUpdateStorageSetting(@PathVariable String id) {
        storageSettingService.deleteStorageSettingById(id);
        return Result.ok();
    }

    @Operation(summary = "获取用户已启用存储平台列表")
    @GetMapping("/active-platforms")
    public Result<List<StorageActivePlatformsVO>> getActiveStoragePlatforms() {
        List<StorageActivePlatformsVO> settings = storageSettingService.getActiveStoragePlatforms();
        return Result.ok(settings);
    }
}
