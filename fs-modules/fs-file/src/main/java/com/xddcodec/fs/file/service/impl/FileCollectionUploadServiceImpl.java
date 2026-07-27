package com.xddcodec.fs.file.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.xddcodec.fs.file.cache.TransferTaskCacheManager;
import com.xddcodec.fs.file.domain.FileCollection;
import com.xddcodec.fs.file.domain.FileCollectionSubmission;
import com.xddcodec.fs.file.domain.FileInfo;
import com.xddcodec.fs.file.domain.FileTransferTask;
import com.xddcodec.fs.file.domain.dto.CheckUploadCmd;
import com.xddcodec.fs.file.domain.dto.InitUploadCmd;
import com.xddcodec.fs.file.domain.vo.CheckUploadResultVO;
import com.xddcodec.fs.file.domain.vo.FileCollectionUploadContext;
import com.xddcodec.fs.file.enums.TransferTaskStatus;
import com.xddcodec.fs.file.enums.TransferTaskType;
import com.xddcodec.fs.file.mapper.FileTransferTaskMapper;
import com.xddcodec.fs.file.service.FileCollectionService;
import com.xddcodec.fs.file.service.FileCollectionUploadService;
import com.xddcodec.fs.file.service.FileInfoService;
import com.xddcodec.fs.file.service.FileObjectReferenceService;
import com.xddcodec.fs.file.service.FileTransferTaskService;
import com.xddcodec.fs.framework.common.exception.BusinessException;
import com.xddcodec.fs.framework.common.exception.StorageOperationException;
import com.xddcodec.fs.framework.common.utils.FileUtils;
import com.xddcodec.fs.log.constant.OperationType;
import com.xddcodec.fs.log.service.SysOperationLogService;
import com.xddcodec.fs.storage.facade.StorageServiceFacade;
import com.xddcodec.fs.storage.plugin.core.IStorageOperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.xddcodec.fs.file.domain.table.FileTransferTaskTableDef.FILE_TRANSFER_TASK;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileCollectionUploadServiceImpl implements FileCollectionUploadService {

    private static final long MIN_CHUNK_SIZE = 5L * 1024 * 1024;
    private static final long MAX_CHUNK_SIZE = 16L * 1024 * 1024;
    private static final int MAX_CHUNKS = 10_000;
    private static final int MAX_FILES_PER_SUBMISSION = 1_000;

    private final FileCollectionService fileCollectionService;
    private final FileTransferTaskService transferTaskService;
    private final FileTransferTaskMapper transferTaskMapper;
    private final FileInfoService fileInfoService;
    private final FileObjectReferenceService objectReferenceService;
    private final TransferTaskCacheManager cacheManager;
    private final StorageServiceFacade storageServiceFacade;
    private final SysOperationLogService operationLogService;

    @Value("${spring.application.name:free-fs}")
    private String applicationName;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String initUpload(String collectionId, String submissionId,
                             String uploadToken, InitUploadCmd cmd) {
        FileCollectionUploadContext context = fileCollectionService.authorizeUpload(
                collectionId, submissionId, uploadToken);
        FileCollection collection = context.getCollection();
        FileCollectionSubmission submission = context.getSubmission();

        long existingTasks = transferTaskService.count(new QueryWrapper()
                .where(FILE_TRANSFER_TASK.COLLECTION_SUBMISSION_ID.eq(submissionId)));
        if (existingTasks >= MAX_FILES_PER_SUBMISSION) {
            throw new BusinessException(400, "单次提交最多上传1000个文件");
        }

        String fileName = sanitizeFileName(cmd.getFileName());
        validateUploadMetadata(collection, fileName, cmd);
        String suffix = FileUtils.getSuffix(fileName);
        String objectName = IdUtil.fastSimpleUUID()
                + (StrUtil.isBlank(suffix) ? "" : "." + suffix);
        String objectKey = FileUtils.generateObjectKey(
                applicationName, collection.getUserId(), objectName);

        FileTransferTask task = new FileTransferTask();
        task.setTaskId(IdUtil.fastSimpleUUID());
        task.setUserId(collection.getUserId());
        task.setWorkspaceId(collection.getWorkspaceId());
        task.setCollectionId(collectionId);
        task.setCollectionSubmissionId(submissionId);
        task.setParentId(submission.getFolderId());
        task.setObjectKey(objectKey);
        task.setFileName(fileName);
        task.setFileSize(cmd.getFileSize());
        task.setSuffix(suffix);
        task.setMimeType(trim(StrUtil.blankToDefault(
                cmd.getMimeType(), "application/octet-stream"), 255));
        task.setTotalChunks(cmd.getTotalChunks());
        task.setUploadedChunks(0);
        task.setTaskType(TransferTaskType.upload);
        task.setChunkSize(cmd.getChunkSize());
        task.setStoragePlatformSettingId(collection.getStoragePlatformSettingId());
        task.setStatus(TransferTaskStatus.initialized);
        task.setStartTime(LocalDateTime.now());
        transferTaskService.save(task);
        cacheManager.cacheTask(task);
        cacheManager.recordStartTime(task.getTaskId());
        return task.getTaskId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckUploadResultVO checkUpload(String collectionId, String submissionId,
                                           String uploadToken, CheckUploadCmd cmd) {
        AuthorizedCollectionTask authorized = authorizeTask(
                collectionId, submissionId, uploadToken, cmd.getTaskId());
        FileTransferTask task = authorized.task();
        if (TransferTaskStatus.completed.equals(task.getStatus()) && StrUtil.isNotBlank(task.getFileId())) {
            return CheckUploadResultVO.builder()
                    .isQuickUpload(true)
                    .taskId(task.getTaskId())
                    .fileId(task.getFileId())
                    .message("文件已经上传完成")
                    .build();
        }
        if (!TransferTaskStatus.initialized.equals(task.getStatus())) {
            throw new BusinessException(400, "上传任务状态不正确");
        }
        task.setStatus(TransferTaskStatus.checking);
        task.setFileMd5(cmd.getFileMd5());
        task.setUpdatedAt(LocalDateTime.now());
        transferTaskService.updateById(task);
        cacheManager.cacheTask(task);

        String storageSettingId = task.getStoragePlatformSettingId();
        try (FileObjectReferenceService.ReferenceLock ignored =
                     objectReferenceService.acquireContentLock(
                             storageSettingId, cmd.getFileMd5(), task.getFileSize())) {
            FileInfo reusableFile = objectReferenceService.findReusableFile(
                    cmd.getFileMd5(), task.getFileSize(), storageSettingId);
            if (reusableFile != null) {
                try (FileObjectReferenceService.ReferenceLock objectLock =
                             objectReferenceService.acquireObjectLock(
                                     reusableFile.getStoragePlatformSettingId(), reusableFile.getObjectKey())) {
                    FileInfo fileInfo = completeQuickUpload(
                            authorized, task, reusableFile.getObjectKey(), cmd.getFileMd5());
                    return CheckUploadResultVO.builder()
                            .isQuickUpload(true)
                            .taskId(task.getTaskId())
                            .fileId(fileInfo.getId())
                            .message("秒传成功")
                            .build();
                }
            }

            if (task.getFileSize() == null || task.getFileSize() == 0) {
                IStorageOperationService storageService =
                        storageServiceFacade.getStorageService(storageSettingId);
                storageService.uploadFile(new ByteArrayInputStream(new byte[0]), task.getObjectKey());
                FileInfo fileInfo = completeQuickUpload(
                        authorized, task, task.getObjectKey(), cmd.getFileMd5());
                return CheckUploadResultVO.builder()
                        .isQuickUpload(true)
                        .taskId(task.getTaskId())
                        .fileId(fileInfo.getId())
                        .message("空文件上传成功")
                        .build();
            }
        }

        IStorageOperationService storageService =
                storageServiceFacade.getStorageService(storageSettingId);
        String uploadId = storageService.initiateMultipartUpload(
                task.getObjectKey(), task.getMimeType());
        task.setUploadId(uploadId);
        task.setStatus(TransferTaskStatus.uploading);
        task.setUpdatedAt(LocalDateTime.now());
        transferTaskService.updateById(task);
        cacheManager.cacheTask(task);
        return CheckUploadResultVO.builder()
                .isQuickUpload(false)
                .taskId(task.getTaskId())
                .uploadId(uploadId)
                .message("文件校验完成")
                .build();
    }

    @Override
    public void uploadChunk(String collectionId, String submissionId,
                            String uploadToken, String taskId, Integer chunkIndex,
                            String chunkMd5, MultipartFile file) {
        AuthorizedCollectionTask authorized = authorizeTask(
                collectionId, submissionId, uploadToken, taskId);
        FileTransferTask task = authorized.task();
        if (!TransferTaskStatus.uploading.equals(task.getStatus())) {
            throw new BusinessException(400, "上传任务当前不能接收分片");
        }
        validateChunk(task, chunkIndex, file);
        if (cacheManager.isChunkTransferred(taskId, chunkIndex)) {
            return;
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new StorageOperationException("读取上传分片失败", e);
        }
        if (StrUtil.isBlank(chunkMd5)
                || !DigestUtil.md5Hex(bytes).equalsIgnoreCase(chunkMd5.trim())) {
            throw new BusinessException(400, "上传分片校验失败");
        }

        IStorageOperationService storageService =
                storageServiceFacade.getStorageService(task.getStoragePlatformSettingId());
        String eTag;
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            eTag = storageService.uploadPart(
                    task.getObjectKey(), task.getUploadId(), chunkIndex,
                    bytes.length, inputStream);
        } catch (IOException e) {
            throw new StorageOperationException("关闭上传分片失败", e);
        }
        cacheManager.addTransferredChunk(taskId, chunkIndex, eTag);
        cacheManager.recordTransferredBytes(taskId, bytes.length);
    }

    @Override
    public Set<Integer> getUploadedChunks(String collectionId, String submissionId,
                                          String uploadToken, String taskId) {
        authorizeTask(collectionId, submissionId, uploadToken, taskId);
        return cacheManager.getTransferredChunkList(taskId).keySet();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfo mergeChunks(String collectionId, String submissionId,
                                String uploadToken, String taskId) {
        AuthorizedCollectionTask authorized = authorizeTask(
                collectionId, submissionId, uploadToken, taskId);
        String lockKey = "file-collection:merge:" + taskId;
        if (!cacheManager.tryLock(lockKey, 300)) {
            throw new BusinessException(409, "文件正在合并，请勿重复提交");
        }
        try {
            FileTransferTask task = getTask(taskId);
            authorized = new AuthorizedCollectionTask(
                    authorized.context(), task);
            if (TransferTaskStatus.completed.equals(task.getStatus())
                    && StrUtil.isNotBlank(task.getFileId())) {
                FileInfo completedFile = fileInfoService.getById(task.getFileId());
                if (completedFile != null) {
                    return completedFile;
                }
            }
            if (!TransferTaskStatus.uploading.equals(task.getStatus())) {
                throw new BusinessException(400, "上传任务当前不能合并");
            }

            Map<Integer, String> chunkETags = cacheManager.getTransferredChunkList(taskId);
            if (chunkETags.size() != task.getTotalChunks()) {
                throw new BusinessException(400,
                        "分片不完整：已上传 " + chunkETags.size() + "/" + task.getTotalChunks());
            }
            List<Map<String, Object>> partETags = new ArrayList<>();
            for (int i = 0; i < task.getTotalChunks(); i++) {
                String eTag = chunkETags.get(i);
                if (StrUtil.isBlank(eTag)) {
                    throw new BusinessException(400, "缺少分片 " + i);
                }
                partETags.add(Map.of("partNumber", i, "eTag", eTag));
            }

            task.setStatus(TransferTaskStatus.merging);
            task.setUpdatedAt(LocalDateTime.now());
            transferTaskService.updateById(task);
            cacheManager.cacheTask(task);
            IStorageOperationService storageService =
                    storageServiceFacade.getStorageService(task.getStoragePlatformSettingId());
            storageService.completeMultipartUpload(
                    task.getObjectKey(), task.getUploadId(), partETags);

            String uploadedObjectKey = task.getObjectKey();
            FileInfo fileInfo;
            try (FileObjectReferenceService.ReferenceLock ignored =
                         objectReferenceService.acquireContentLock(
                                 task.getStoragePlatformSettingId(), task.getFileMd5(), task.getFileSize())) {
                FileInfo reusableFile = objectReferenceService.findReusableFile(
                        task.getFileMd5(), task.getFileSize(), task.getStoragePlatformSettingId());
                if (reusableFile != null) {
                    try (FileObjectReferenceService.ReferenceLock objectLock =
                                 objectReferenceService.acquireObjectLock(
                                         reusableFile.getStoragePlatformSettingId(), reusableFile.getObjectKey())) {
                        fileInfo = buildFileInfo(task, reusableFile.getObjectKey());
                        fileInfoService.save(fileInfo);
                    }
                } else {
                    fileInfo = buildFileInfo(task, uploadedObjectKey);
                    fileInfoService.save(fileInfo);
                }
            }

            if (!Objects.equals(uploadedObjectKey, fileInfo.getObjectKey())) {
                try {
                    storageService.deleteFile(uploadedObjectKey);
                } catch (Exception deleteError) {
                    log.warn("删除文件收集产生的重复对象失败: taskId={}, objectKey={}",
                            taskId, uploadedObjectKey, deleteError);
                }
            }
            markTaskCompleted(authorized, task, fileInfo);
            return fileInfo;
        } finally {
            cacheManager.releaseLock(lockKey);
        }
    }

    private FileInfo completeQuickUpload(AuthorizedCollectionTask authorized,
                                         FileTransferTask task,
                                         String objectKey,
                                         String fileMd5) {
        FileInfo fileInfo = buildFileInfo(task, objectKey);
        fileInfo.setContentMd5(fileMd5);
        fileInfoService.save(fileInfo);
        markTaskCompleted(authorized, task, fileInfo);
        return fileInfo;
    }

    private void markTaskCompleted(AuthorizedCollectionTask authorized,
                                   FileTransferTask task,
                                   FileInfo fileInfo) {
        LocalDateTime now = LocalDateTime.now();
        int completedRows = transferTaskMapper.completeCollectionUpload(
                task.getTaskId(), fileInfo.getId(), TransferTaskStatus.completed, now);
        if (completedRows != 1) {
            throw new BusinessException(409, "文件已经上传完成，请勿重复提交");
        }
        fileCollectionService.recordUploadedFile(
                task.getCollectionId(), task.getCollectionSubmissionId(), task.getFileSize());
        FileCollectionSubmission submission = authorized.context().getSubmission();
        operationLogService.recordSuccessAs(
                task.getWorkspaceId(),
                "collection:" + submission.getId(),
                submission.getSubmitterName(),
                OperationType.COLLECTION_UPLOAD,
                "文件收集上传",
                "FILE",
                fileInfo.getId(),
                fileInfo.getDisplayName(),
                "收集名称: " + authorized.context().getCollection().getCollectionName()
                        + ", 文件大小: " + FileUtils.formatFileSize(fileInfo.getSize()));
        cacheManager.cleanTask(task.getTaskId());
    }

    private FileInfo buildFileInfo(FileTransferTask task, String objectKey) {
        LocalDateTime now = LocalDateTime.now();
        String displayName = fileInfoService.generateUniqueName(
                task.getWorkspaceId(), task.getParentId(), task.getFileName(),
                false, null, task.getStoragePlatformSettingId());
        FileInfo fileInfo = new FileInfo();
        fileInfo.setId(IdUtil.fastSimpleUUID());
        fileInfo.setObjectKey(objectKey);
        fileInfo.setOriginalName(task.getFileName());
        fileInfo.setDisplayName(displayName);
        fileInfo.setSuffix(task.getSuffix());
        fileInfo.setSize(task.getFileSize());
        fileInfo.setMimeType(task.getMimeType());
        fileInfo.setIsDir(false);
        fileInfo.setParentId(task.getParentId());
        fileInfo.setWorkspaceId(task.getWorkspaceId());
        fileInfo.setUserId(task.getUserId());
        fileInfo.setContentMd5(task.getFileMd5());
        fileInfo.setStoragePlatformSettingId(task.getStoragePlatformSettingId());
        fileInfo.setUploadTime(now);
        fileInfo.setUpdateTime(now);
        fileInfo.setIsDeleted(false);
        return fileInfo;
    }

    private AuthorizedCollectionTask authorizeTask(
            String collectionId, String submissionId, String uploadToken, String taskId) {
        FileCollectionUploadContext context = fileCollectionService.authorizeUpload(
                collectionId, submissionId, uploadToken);
        FileTransferTask task = getTask(taskId);
        if (!Objects.equals(task.getCollectionId(), collectionId)
                || !Objects.equals(task.getCollectionSubmissionId(), submissionId)
                || !Objects.equals(task.getWorkspaceId(), context.getCollection().getWorkspaceId())
                || !Objects.equals(task.getParentId(), context.getSubmission().getFolderId())) {
            throw new BusinessException(403, "无权访问该上传任务");
        }
        return new AuthorizedCollectionTask(context, task);
    }

    private FileTransferTask getTask(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            throw new BusinessException(400, "上传任务ID不能为空");
        }
        FileTransferTask task = transferTaskService.getOne(new QueryWrapper()
                .where(FILE_TRANSFER_TASK.TASK_ID.eq(taskId)));
        if (task == null || StrUtil.isBlank(task.getCollectionSubmissionId())) {
            throw new BusinessException(404, "上传任务不存在");
        }
        return task;
    }

    private void validateUploadMetadata(FileCollection collection,
                                        String fileName,
                                        InitUploadCmd cmd) {
        if (cmd.getFileSize() == null || cmd.getFileSize() < 0) {
            throw new BusinessException(400, "文件大小不正确");
        }
        if (collection.getMaxFileSize() != null
                && cmd.getFileSize() > collection.getMaxFileSize()) {
            throw new BusinessException(400, "文件超过该收集设置的单文件大小限制");
        }
        if (cmd.getChunkSize() == null
                || cmd.getChunkSize() < MIN_CHUNK_SIZE
                || cmd.getChunkSize() > MAX_CHUNK_SIZE) {
            throw new BusinessException(400, "分片大小必须在5MB到16MB之间");
        }
        int expectedChunks = cmd.getFileSize() == 0
                ? 0 : (int) Math.ceil((double) cmd.getFileSize() / cmd.getChunkSize());
        if (cmd.getTotalChunks() == null
                || cmd.getTotalChunks() != expectedChunks
                || expectedChunks > MAX_CHUNKS) {
            throw new BusinessException(400, "分片数量不正确");
        }
        if (StrUtil.isNotBlank(collection.getAllowedExtensions())) {
            String suffix = FileUtils.getSuffix(fileName).toLowerCase(Locale.ROOT);
            Set<String> allowed = Set.of(collection.getAllowedExtensions().split(","));
            if (StrUtil.isBlank(suffix) || !allowed.contains(suffix)) {
                throw new BusinessException(400, "该文件类型不在允许上传的范围内");
            }
        }
    }

    private void validateChunk(FileTransferTask task,
                               Integer chunkIndex,
                               MultipartFile file) {
        if (chunkIndex == null || chunkIndex < 0 || chunkIndex >= task.getTotalChunks()) {
            throw new BusinessException(400, "分片序号不正确");
        }
        long start = (long) chunkIndex * task.getChunkSize();
        long expectedSize = Math.min(task.getChunkSize(), task.getFileSize() - start);
        if (expectedSize < 0 || file == null || file.getSize() != expectedSize) {
            throw new BusinessException(400, "分片大小不正确");
        }
        if (file.getSize() > MAX_CHUNK_SIZE) {
            throw new BusinessException(400, "分片过大");
        }
    }

    private String sanitizeFileName(String value) {
        String sanitized = value == null ? "" : value
                .replaceAll("[\\p{Cntrl}/\\\\]", "_")
                .trim();
        sanitized = sanitized.replaceAll("^[. ]+|[. ]+$", "");
        if (sanitized.isBlank()) {
            throw new BusinessException(400, "文件名不能为空");
        }
        if (sanitized.length() <= 128) {
            return sanitized;
        }
        String suffix = FileUtils.getSuffix(sanitized);
        if (StrUtil.isBlank(suffix)) {
            return sanitized.substring(0, 128);
        }
        int suffixLength = Math.min(suffix.length() + 1, 21);
        return sanitized.substring(0, 128 - suffixLength)
                + "." + suffix.substring(0, suffixLength - 1);
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record AuthorizedCollectionTask(
            FileCollectionUploadContext context,
            FileTransferTask task) {
    }
}
