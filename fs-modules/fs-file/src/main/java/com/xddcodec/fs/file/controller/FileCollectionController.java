package com.xddcodec.fs.file.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.xddcodec.fs.file.domain.dto.CreateFileCollectionCmd;
import com.xddcodec.fs.file.domain.dto.UpdateFileCollectionStatusCmd;
import com.xddcodec.fs.file.domain.qry.FileCollectionQry;
import com.xddcodec.fs.file.domain.qry.FileCollectionSubmissionQry;
import com.xddcodec.fs.file.domain.vo.FileCollectionDeletionResult;
import com.xddcodec.fs.file.domain.vo.FileCollectionSubmissionVO;
import com.xddcodec.fs.file.domain.vo.FileCollectionVO;
import com.xddcodec.fs.file.service.FileCollectionService;
import com.xddcodec.fs.framework.common.domain.PageResult;
import com.xddcodec.fs.framework.common.domain.Result;
import com.xddcodec.fs.log.constant.OperationType;
import com.xddcodec.fs.log.service.SysOperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/apis/file-collections")
@Tag(name = "文件收集", description = "文件收集管理")
public class FileCollectionController {

    private final FileCollectionService fileCollectionService;
    private final SysOperationLogService operationLogService;

    @GetMapping("/pages")
    @Operation(summary = "分页获取文件收集")
    public PageResult<FileCollectionVO> getPages(@Validated FileCollectionQry qry) {
        return fileCollectionService.getPages(qry);
    }

    @GetMapping("/{collectionId}")
    @Operation(summary = "获取文件收集详情")
    public Result<FileCollectionVO> getDetail(@PathVariable String collectionId) {
        return Result.ok(fileCollectionService.getDetail(collectionId));
    }

    @PostMapping("/create")
    @SaCheckPermission(value = {"file:share", "file:write"}, mode = SaMode.AND)
    @Operation(summary = "创建文件收集")
    public Result<FileCollectionVO> createCollection(
            @RequestBody @Validated CreateFileCollectionCmd cmd) {
        FileCollectionVO collection = fileCollectionService.createCollection(cmd);
        operationLogService.recordSuccess(
                OperationType.CREATE_COLLECTION,
                "创建文件收集",
                "FILE_COLLECTION",
                collection.getId(),
                collection.getCollectionName(),
                "目标文件夹: " + collection.getTargetFolderName());
        return Result.ok(collection);
    }

    @PatchMapping("/{collectionId}/status")
    @SaCheckPermission("file:share")
    @Operation(summary = "关闭或重新开启文件收集")
    public Result<FileCollectionVO> updateStatus(
            @PathVariable String collectionId,
            @RequestBody @Validated UpdateFileCollectionStatusCmd cmd) {
        FileCollectionVO collection = fileCollectionService.updateStatus(
                collectionId, cmd.getStatus());
        operationLogService.recordSuccess(
                OperationType.UPDATE_COLLECTION,
                "更新文件收集状态",
                "FILE_COLLECTION",
                collection.getId(),
                collection.getCollectionName(),
                "状态: " + collection.getStatus());
        return Result.ok(collection);
    }

    @DeleteMapping("/{collectionId}")
    @SaCheckPermission(value = {"file:share", "file:write"}, mode = SaMode.AND)
    @Operation(summary = "删除文件收集")
    public Result<Void> deleteCollection(@PathVariable String collectionId) {
        FileCollectionDeletionResult deletion = fileCollectionService.deleteCollection(collectionId);
        FileCollectionVO collection = deletion.collection();
        operationLogService.recordSuccess(
                OperationType.DELETE_COLLECTION,
                "删除文件收集",
                "FILE_COLLECTION",
                collection.getId(),
                collection.getCollectionName(),
                "提交记录: " + deletion.submissionCount()
                        + "，终态传输任务: " + deletion.terminalTaskCount()
                        + "，已收集文件保留");
        return Result.ok();
    }

    @GetMapping("/{collectionId}/submissions")
    @Operation(summary = "分页获取文件收集提交记录")
    public PageResult<FileCollectionSubmissionVO> getSubmissions(
            @PathVariable String collectionId,
            @Validated FileCollectionSubmissionQry qry) {
        return fileCollectionService.getSubmissions(collectionId, qry);
    }
}
