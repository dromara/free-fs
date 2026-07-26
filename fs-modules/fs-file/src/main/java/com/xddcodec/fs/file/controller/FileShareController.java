package com.xddcodec.fs.file.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xddcodec.fs.file.domain.dto.CreateShareCmd;
import com.xddcodec.fs.file.domain.dto.VerifyShareCodeCmd;
import com.xddcodec.fs.file.domain.qry.FileShareQry;
import com.xddcodec.fs.file.domain.vo.*;
import com.xddcodec.fs.file.service.FileShareAccessRecordService;
import com.xddcodec.fs.file.service.FileShareService;
import com.xddcodec.fs.framework.common.domain.PageResult;
import com.xddcodec.fs.framework.common.domain.Result;
import com.xddcodec.fs.log.constant.OperationType;
import com.xddcodec.fs.log.service.SysOperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Validated
@Slf4j
@RestController
@RequestMapping("/apis/share")
@Tag(name = "文件分享", description = "文件分享")
public class FileShareController {

    @Autowired
    private FileShareService fileShareService;

    @Autowired
    private FileShareAccessRecordService fileShareAccessRecordService;

    @Autowired
    private SysOperationLogService operationLogService;

    @GetMapping("/pages")
    @Operation(summary = "获取我的分享", description = "分页获取我的分享列表")
    public PageResult<FileShareVO> getPages(FileShareQry qry) {

        return fileShareService.getPages(qry);
    }

    @GetMapping("/{shareId}")
    @Operation(summary = "获取分享详细信息", description = "获取分享详细信息")
    public Result<FileShareVO> getDetail(@PathVariable String shareId) {
        FileShareVO result = fileShareService.getDetail(shareId);
        return Result.ok(result);
    }

    @GetMapping("/{shareId}/access/records")
    @Operation(summary = "获取分享访问记录列表", description = "获取分享访问记录列表")
    public Result<List<FileShareAccessRecordVO>> getListByShareId(@PathVariable String shareId) {
        fileShareService.getDetail(shareId);
        List<FileShareAccessRecordVO> result = fileShareAccessRecordService.getListByShareId(shareId);
        return Result.ok(result);
    }

    @PostMapping("/create")
    @Operation(summary = "创建分享", description = "创建分享")
    @SaCheckPermission("file:share")
    public Result<FileShareVO> createDirectory(@RequestBody @Validated CreateShareCmd cmd) {
        FileShareVO fileShareVO = fileShareService.createShare(cmd);
        operationLogService.recordSuccess(
                OperationType.CREATE_SHARE,
                "创建分享",
                "SHARE",
                fileShareVO.getId(),
                fileShareVO.getShareName(),
                "分享文件数: " + cmd.getFileIds().size()
        );
        return Result.ok(fileShareVO);
    }

    @DeleteMapping("/cancels")
    @Operation(summary = "取消分享", description = "取消分享")
    @SaCheckPermission("file:share")
    public Result<FileShareVO> cancelShares(@RequestBody List<String> ids) {
        fileShareService.cancelShares(ids);
        operationLogService.recordSuccess(
                OperationType.CANCEL_SHARE,
                "取消分享",
                ids.size() > 1 ? "MULTIPLE" : "SHARE",
                String.join(",", ids),
                null,
                "共取消 " + ids.size() + " 个分享"
        );
        return Result.ok();
    }

    @DeleteMapping("/clears")
    @Operation(summary = "全部取消分享", description = "全部取消")
    @SaCheckPermission("file:share")
    public Result<FileShareVO> cancelAllShares() {
        fileShareService.cancelAllShares();
        operationLogService.recordSuccess(
                OperationType.CANCEL_SHARE,
                "清空分享",
                "SHARE",
                null,
                "全部分享",
                null
        );
        return Result.ok();
    }

    @Operation(summary = "验证提取码", description = "验证提取码")
    @PostMapping("/verify/code")
    public Result<Boolean> verifyShareCode(@RequestBody @Validated VerifyShareCodeCmd cmd) {
        boolean result = fileShareService.verifyShareCode(cmd);
        return Result.ok(result);
    }

    @Operation(summary = "获取分享页数据", description = "获取分享页数据")
    @GetMapping("/{shareId}/info")
    public Result<FileShareThinVO> getFileShareThin(@PathVariable String shareId) {
        return Result.ok(fileShareService.getFileShareThinVO(shareId));
    }

    @Operation(summary = "获取分享页文件列表数据", description = "获取分享页文件列表数据")
    @GetMapping("/{shareId}/items")
    public Result<List<FileVO>> getShareFileItems(@PathVariable String shareId, @RequestParam(required = false) String parentId) {
        return Result.ok(fileShareService.getShareFileItems(shareId, parentId));
    }

    @Operation(summary = "分享内文件下载", description = "分享内文件下载")
    @GetMapping("/{shareId}/download/{fileId}")
    public ResponseEntity<Resource> downloadShareFile(@PathVariable String shareId, @PathVariable String fileId) {
        try {
            // 获取文件信息和文件流
            FileDownloadVO fileDownload = fileShareService.downloadFiles(shareId, fileId);

            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + URLEncoder.encode(fileDownload.getFileName(), StandardCharsets.UTF_8) + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, "application/octet-stream");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(fileDownload.getFileSize())
                    .body(fileDownload.getResource());
        } catch (Exception e) {
            throw new RuntimeException("文件下载失败", e);
        }
    }

    @PostMapping("/{shareId}/folder-download/tasks/{folderId}")
    @Operation(summary = "创建分享文件夹下载任务", description = "异步打包分享内文件夹")
    public Result<FolderDownloadTaskVO> createFolderDownloadTask(@PathVariable String shareId,
                                                                  @PathVariable String folderId) {
        return Result.ok(fileShareService.createFolderDownloadTask(shareId, folderId));
    }

    @GetMapping("/{shareId}/folder-download/tasks/{taskId}")
    @Operation(summary = "查询分享文件夹下载任务", description = "查询分享文件夹打包进度")
    public Result<FolderDownloadTaskVO> getFolderDownloadTask(@PathVariable String shareId,
                                                               @PathVariable String taskId) {
        return Result.ok(fileShareService.getFolderDownloadTask(shareId, taskId));
    }

    @DeleteMapping("/{shareId}/folder-download/tasks/{taskId}")
    @Operation(summary = "取消分享文件夹下载打包任务", description = "取消分享文件夹打包并清理临时文件")
    public Result<Void> cancelFolderDownloadTask(@PathVariable String shareId,
                                                  @PathVariable String taskId) {
        fileShareService.cancelFolderDownloadTask(shareId, taskId);
        return Result.ok();
    }

    @GetMapping("/{shareId}/folder-download/tasks/{taskId}/file")
    @Operation(summary = "下载分享文件夹压缩包", description = "下载已打包完成的分享文件夹 zip")
    public ResponseEntity<Resource> downloadFolderTaskFile(@PathVariable String shareId,
                                                            @PathVariable String taskId) {
        try {
            FileDownloadVO fileDownload = fileShareService.downloadFolderTaskFile(shareId, taskId);
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + URLEncoder.encode(fileDownload.getFileName(), StandardCharsets.UTF_8) + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, "application/zip");
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(fileDownload.getFileSize())
                    .body(fileDownload.getResource());
        } catch (Exception e) {
            throw new RuntimeException("分享文件夹下载失败", e);
        }
    }
}
