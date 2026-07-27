package com.xddcodec.fs.file.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.xddcodec.fs.file.domain.dto.CheckUploadCmd;
import com.xddcodec.fs.file.domain.dto.InitDownloadCmd;
import com.xddcodec.fs.file.domain.dto.InitUploadCmd;
import com.xddcodec.fs.file.domain.dto.UploadChunkCmd;
import com.xddcodec.fs.file.domain.FileInfo;
import com.xddcodec.fs.file.domain.qry.DownloadChunkQry;
import com.xddcodec.fs.file.domain.qry.TransferFilesQry;
import com.xddcodec.fs.file.domain.vo.CheckUploadResultVO;
import com.xddcodec.fs.file.domain.vo.FileDownloadVO;
import com.xddcodec.fs.file.domain.vo.FileTransferTaskVO;
import com.xddcodec.fs.file.domain.vo.FolderDownloadTaskVO;
import com.xddcodec.fs.file.domain.vo.InitDownloadResultVO;
import com.xddcodec.fs.file.service.FileTransferTaskService;
import com.xddcodec.fs.framework.common.domain.Result;
import com.xddcodec.fs.framework.common.utils.FileUtils;
import com.xddcodec.fs.framework.sse.SseConnectionManager;
import com.xddcodec.fs.log.constant.OperationType;
import com.xddcodec.fs.log.service.SysOperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * 文件传输控制器
 * 
 * @author xddcode
 */
@Validated
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/apis/transfer")
@Tag(name = "文件传输", description = "文件传输")
public class FileTransferController {

    private final FileTransferTaskService fileTransferTaskService;
    private final SseConnectionManager sseConnectionManager;
    private final SysOperationLogService operationLogService;

    @GetMapping("/files")
    @Operation(summary = "获取传输列表", description = "获取传输列表")
    public Result<List<FileTransferTaskVO>> getTransferFiles(TransferFilesQry qry) {
        List<FileTransferTaskVO> result = fileTransferTaskService.getTransferFiles(qry);
        return Result.ok(result);
    }

    @GetMapping("/sse")
    @Operation(summary = "建立SSE连接", description = "建立SSE连接以接收实时传输事件")
    public ResponseEntity<?> subscribe() {
        if (!StpUtil.isLogin()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Result.error(HttpStatus.UNAUTHORIZED.value(), "未登录", null));
        }
        String userId = StpUtil.getLoginIdAsString();
        log.info("User {} requesting SSE connection", userId);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(sseConnectionManager.createConnection(userId));
    }

    @PostMapping("/init")
    @Operation(summary = "初始化文件上传", description = "初始化上传环境，返回taskId用于后续分片上传")
    public Result<String> initUpload(@RequestBody @Validated InitUploadCmd cmd) {
        String taskId = fileTransferTaskService.initUpload(cmd);
        return Result.ok(taskId, "初始化成功");
    }

    @PostMapping("/check")
    @Operation(summary = "校验文件", description = "前端计算完MD5后调用，判断是否秒传")
    public Result<CheckUploadResultVO> checkUpload(@RequestBody @Validated CheckUploadCmd cmd) {
        CheckUploadResultVO result = fileTransferTaskService.checkUpload(cmd);
        return Result.ok(result);
    }

    @PostMapping("/chunk")
    @Operation(summary = "上传分片", description = "异步上传分片，立即返回，通过SSE推送进度")
    public Result<?> uploadChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("taskId") String taskId,
            @RequestParam("chunkIndex") Integer chunkIndex,
            @RequestParam("chunkMd5") String chunkMd5
    ) throws Exception {
        UploadChunkCmd cmd = new UploadChunkCmd();
        cmd.setTaskId(taskId);
        cmd.setChunkIndex(chunkIndex);
        cmd.setChunkMd5(chunkMd5);
        byte[] fileBytes = file.getBytes();
        fileTransferTaskService.uploadChunk(fileBytes, cmd);
        return Result.ok(null, "分片接收成功，正在处理");
    }

    @PostMapping("/pause/{taskId}")
    @Operation(summary = "暂停传输")
    public Result<Void> pauseTransfer(@PathVariable String taskId) {
        fileTransferTaskService.pauseTransfer(taskId);
        return Result.ok();
    }

    @PostMapping("/resume/{taskId}")
    @Operation(summary = "继续传输")
    public Result<Void> resumeTransfer(@PathVariable String taskId) {
        fileTransferTaskService.resumeTransfer(taskId);
        return Result.ok();
    }

    @DeleteMapping("/cancel/{taskId}")
    @Operation(summary = "取消传输")
    public Result<Void> cancelUpload(@PathVariable String taskId) {
        fileTransferTaskService.cancelTransfer(taskId);
        return Result.ok();
    }

    @PostMapping("/merge/{taskId}")
    @Operation(summary = "合并分片", description = "所有分片上传完成后调用，合并分片并返回文件ID")
    public Result<String> mergeChunks(@PathVariable String taskId) {
        FileInfo fileInfo = fileTransferTaskService.mergeChunks(taskId);
        return Result.ok(fileInfo.getId(), "合并成功");
    }

    @GetMapping("/chunks/{taskId}")
    @Operation(summary = "查询已上传的分片", description = "用于断点续传，返回已上传的分片索引列表")
    public Result<Set<Integer>> getUploadedChunks(@PathVariable String taskId) {
        Set<Integer> uploadedChunks = fileTransferTaskService.getUploadedChunks(taskId);
        return Result.ok(uploadedChunks);
    }

    @DeleteMapping("/clears")
    @Operation(summary = "清空已完成的传输列表", description = "清空已完成的传输列表")
    public Result<Set<Integer>> clearTransfers() {
        fileTransferTaskService.clearTransfers();
        return Result.ok();
    }

    @PostMapping("/init-download")
    @Operation(summary = "初始化下载任务", description = "创建下载任务并返回任务信息")
    public Result<InitDownloadResultVO> initDownload(@RequestBody @Validated InitDownloadCmd cmd) {
        InitDownloadResultVO result = fileTransferTaskService.initDownload(cmd);
        return Result.ok(result, "初始化下载任务成功");
    }

    @GetMapping("/download/chunk")
    @Operation(summary = "下载分片", description = "下载指定分片，返回206 Partial Content")
    public ResponseEntity<StreamingResponseBody> downloadChunk(@Validated DownloadChunkQry qry) {
        try {
            // 获取任务信息
            com.xddcodec.fs.file.domain.FileTransferTask task = fileTransferTaskService.getTask(qry.getTaskId());
            
            // 计算字节范围
            long startByte = (long) qry.getChunkIndex() * task.getChunkSize();
            long endByte = Math.min(startByte + task.getChunkSize() - 1, task.getFileSize() - 1);
            long contentLength = endByte - startByte + 1;
            
            // 创建流式响应
            StreamingResponseBody responseBody = outputStream -> {
                try (InputStream inputStream = fileTransferTaskService.downloadChunk(
                        qry.getTaskId(), qry.getChunkIndex())) {
                    IOUtils.copy(inputStream, outputStream);
                    outputStream.flush();
                } catch (Exception e) {
                    log.error("下载分片失败: taskId={}, chunkIndex={}", qry.getTaskId(), qry.getChunkIndex(), e);
                    throw new RuntimeException("下载分片失败", e);
                }
            };
            
            // 设置响应头
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .header(HttpHeaders.CONTENT_RANGE, 
                            String.format("bytes %d-%d/%d", startByte, endByte, task.getFileSize()))
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + URLEncoder.encode(task.getFileName(), StandardCharsets.UTF_8) + "\"")
                    .body(responseBody);
        } catch (Exception e) {
            log.error("下载分片失败: taskId={}, chunkIndex={}", qry.getTaskId(), qry.getChunkIndex(), e);
            throw new RuntimeException("下载分片失败", e);
        }
    }

    @GetMapping("/download/chunks/{taskId}")
    @Operation(summary = "查询已下载分片", description = "获取已下载的分片索引列表")
    public Result<Set<Integer>> getDownloadedChunks(@PathVariable String taskId) {
        Set<Integer> downloadedChunks = fileTransferTaskService.getDownloadedChunks(taskId);
        return Result.ok(downloadedChunks);
    }

    @GetMapping("/download/{fileId}")
    @Operation(summary = "下载文件", description = "根据文件ID下载文件")
    @Parameter(name = "fileId", description = "文件ID", in = ParameterIn.PATH, required = true)
    public ResponseEntity<Resource> downloadFile(@Parameter(description = "文件ID") @PathVariable("fileId") String fileId) {

        try {
            // 获取文件信息和文件流
            FileDownloadVO fileDownload = fileTransferTaskService.downloadFile(fileId);

            operationLogService.recordSuccess(
                    OperationType.DOWNLOAD,
                    "下载文件",
                    "FILE",
                    fileId,
                    fileDownload.getFileName(),
                    "文件大小: " + FileUtils.formatFileSize(fileDownload.getFileSize())
            );

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

    @PostMapping("/folder-download/tasks/{folderId}")
    @Operation(summary = "创建文件夹下载任务", description = "异步打包文件夹，并返回打包进度")
    public Result<FolderDownloadTaskVO> createFolderDownloadTask(@PathVariable String folderId) {
        FolderDownloadTaskVO result = fileTransferTaskService.createFolderDownloadTask(folderId);
        return Result.ok(result, "文件夹下载任务已创建");
    }

    @GetMapping("/folder-download/tasks/{taskId}")
    @Operation(summary = "查询文件夹下载任务进度", description = "查询文件夹打包进度")
    public Result<FolderDownloadTaskVO> getFolderDownloadTask(@PathVariable String taskId) {
        FolderDownloadTaskVO result = fileTransferTaskService.getFolderDownloadTask(taskId);
        return Result.ok(result);
    }

    @DeleteMapping("/folder-download/tasks/{taskId}")
    @Operation(summary = "取消文件夹下载打包任务", description = "取消正在进行的文件夹打包并清理临时文件")
    public Result<Void> cancelFolderDownloadTask(@PathVariable String taskId) {
        fileTransferTaskService.cancelFolderDownloadTask(taskId);
        return Result.ok();
    }

    @GetMapping("/folder-download/tasks/{taskId}/file")
    @Operation(summary = "下载文件夹压缩包", description = "下载已打包完成的文件夹 zip")
    public ResponseEntity<Resource> downloadFolderTaskFile(@PathVariable String taskId) {
        try {
            FileDownloadVO fileDownload = fileTransferTaskService.downloadFolderTaskFile(taskId);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + URLEncoder.encode(fileDownload.getFileName(), StandardCharsets.UTF_8) + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, "application/zip");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(fileDownload.getFileSize())
                    .body(fileDownload.getResource());
        } catch (Exception e) {
            throw new RuntimeException("文件夹下载失败", e);
        }
    }
}
