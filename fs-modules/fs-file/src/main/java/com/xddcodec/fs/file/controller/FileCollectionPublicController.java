package com.xddcodec.fs.file.controller;

import com.xddcodec.fs.file.domain.FileInfo;
import com.xddcodec.fs.file.domain.dto.CheckUploadCmd;
import com.xddcodec.fs.file.domain.dto.CreateFileCollectionSubmissionCmd;
import com.xddcodec.fs.file.domain.dto.InitUploadCmd;
import com.xddcodec.fs.file.domain.vo.CheckUploadResultVO;
import com.xddcodec.fs.file.domain.vo.FileCollectionPublicVO;
import com.xddcodec.fs.file.domain.vo.FileCollectionSubmissionSessionVO;
import com.xddcodec.fs.file.service.FileCollectionService;
import com.xddcodec.fs.file.service.FileCollectionUploadService;
import com.xddcodec.fs.framework.common.domain.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/apis/file-collections/public")
@Tag(name = "公开文件收集", description = "无需登录的文件收集上传接口")
public class FileCollectionPublicController {

    public static final String UPLOAD_TOKEN_HEADER = "X-Collection-Upload-Token";

    private final FileCollectionService fileCollectionService;
    private final FileCollectionUploadService uploadService;

    @GetMapping("/{collectionId}")
    @Operation(summary = "获取公开收集信息")
    public Result<FileCollectionPublicVO> getPublicInfo(@PathVariable String collectionId) {
        return Result.ok(fileCollectionService.getPublicInfo(collectionId));
    }

    @PostMapping("/{collectionId}/submissions")
    @Operation(summary = "创建匿名提交会话")
    public Result<FileCollectionSubmissionSessionVO> startSubmission(
            @PathVariable String collectionId,
            @RequestBody @Validated CreateFileCollectionSubmissionCmd cmd) {
        return Result.ok(fileCollectionService.startSubmission(collectionId, cmd));
    }

    @PostMapping("/{collectionId}/submissions/{submissionId}/uploads/init")
    @Operation(summary = "初始化收集文件上传")
    public Result<String> initUpload(
            @PathVariable String collectionId,
            @PathVariable String submissionId,
            @RequestHeader(UPLOAD_TOKEN_HEADER) String uploadToken,
            @RequestBody @Validated InitUploadCmd cmd) {
        return Result.ok(uploadService.initUpload(
                collectionId, submissionId, uploadToken, cmd));
    }

    @PostMapping("/{collectionId}/submissions/{submissionId}/uploads/check")
    @Operation(summary = "校验收集文件并判断是否秒传")
    public Result<CheckUploadResultVO> checkUpload(
            @PathVariable String collectionId,
            @PathVariable String submissionId,
            @RequestHeader(UPLOAD_TOKEN_HEADER) String uploadToken,
            @RequestBody @Validated CheckUploadCmd cmd) {
        return Result.ok(uploadService.checkUpload(
                collectionId, submissionId, uploadToken, cmd));
    }

    @PostMapping("/{collectionId}/submissions/{submissionId}/uploads/chunk")
    @Operation(summary = "上传收集文件分片")
    public Result<Void> uploadChunk(
            @PathVariable String collectionId,
            @PathVariable String submissionId,
            @RequestHeader(UPLOAD_TOKEN_HEADER) String uploadToken,
            @RequestParam("file") MultipartFile file,
            @RequestParam("taskId") String taskId,
            @RequestParam("chunkIndex") Integer chunkIndex,
            @RequestParam("chunkMd5") String chunkMd5) {
        uploadService.uploadChunk(collectionId, submissionId, uploadToken,
                taskId, chunkIndex, chunkMd5, file);
        return Result.ok();
    }

    @GetMapping("/{collectionId}/submissions/{submissionId}/uploads/{taskId}/chunks")
    @Operation(summary = "查询收集文件已上传分片")
    public Result<Set<Integer>> getUploadedChunks(
            @PathVariable String collectionId,
            @PathVariable String submissionId,
            @PathVariable String taskId,
            @RequestHeader(UPLOAD_TOKEN_HEADER) String uploadToken) {
        return Result.ok(uploadService.getUploadedChunks(
                collectionId, submissionId, uploadToken, taskId));
    }

    @PostMapping("/{collectionId}/submissions/{submissionId}/uploads/{taskId}/merge")
    @Operation(summary = "合并收集文件分片")
    public Result<String> mergeChunks(
            @PathVariable String collectionId,
            @PathVariable String submissionId,
            @PathVariable String taskId,
            @RequestHeader(UPLOAD_TOKEN_HEADER) String uploadToken) {
        FileInfo fileInfo = uploadService.mergeChunks(
                collectionId, submissionId, uploadToken, taskId);
        return Result.ok(fileInfo.getId());
    }

    @PostMapping("/{collectionId}/submissions/{submissionId}/complete")
    @Operation(summary = "完成文件收集提交")
    public Result<Void> completeSubmission(
            @PathVariable String collectionId,
            @PathVariable String submissionId,
            @RequestHeader(UPLOAD_TOKEN_HEADER) String uploadToken) {
        fileCollectionService.completeSubmission(collectionId, submissionId, uploadToken);
        return Result.ok();
    }
}
