package com.xddcodec.fs.file.service.impl;

import cn.dev33.satoken.stp.StpUtil;

import cn.hutool.core.util.IdUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.xddcodec.fs.file.cache.TransferTaskCacheManager;
import com.xddcodec.fs.file.domain.FileInfo;
import com.xddcodec.fs.file.domain.FileTransferTask;
import com.xddcodec.fs.file.domain.dto.CheckUploadCmd;
import com.xddcodec.fs.file.domain.dto.InitDownloadCmd;
import com.xddcodec.fs.file.domain.dto.InitUploadCmd;
import com.xddcodec.fs.file.domain.dto.UploadChunkCmd;
import com.xddcodec.fs.file.domain.qry.TransferFilesQry;
import com.xddcodec.fs.file.domain.vo.CheckUploadResultVO;
import com.xddcodec.fs.file.domain.vo.FileDownloadVO;
import com.xddcodec.fs.file.domain.vo.FileTransferTaskVO;
import com.xddcodec.fs.file.domain.vo.FolderDownloadTaskVO;
import com.xddcodec.fs.file.domain.vo.InitDownloadResultVO;
import com.xddcodec.fs.file.enums.TransferTaskType;
import com.xddcodec.fs.file.handler.UploadTaskExceptionHandler;
import com.xddcodec.fs.file.handler.DownloadTaskExceptionHandler;
import com.xddcodec.fs.file.mapper.FileTransferTaskMapper;
import com.xddcodec.fs.file.service.FileInfoService;
import com.xddcodec.fs.file.service.FileObjectReferenceService;
import com.xddcodec.fs.file.service.FileTransferTaskService;
import com.xddcodec.fs.file.enums.TransferTaskStatus;
import com.xddcodec.fs.framework.common.context.WorkspaceContext;
import com.xddcodec.fs.framework.common.exception.BusinessException;
import com.xddcodec.fs.framework.common.exception.StorageOperationException;
import com.xddcodec.fs.framework.common.utils.ErrorMessageUtils;
import com.xddcodec.fs.framework.common.utils.FileUtils;
import com.xddcodec.fs.framework.common.utils.I18nUtils;
import com.xddcodec.fs.framework.common.utils.StringUtils;
import com.xddcodec.fs.file.service.TransferSseService;
import com.xddcodec.fs.log.constant.OperationType;
import com.xddcodec.fs.log.service.SysOperationLogService;
import com.xddcodec.fs.storage.facade.StorageServiceFacade;
import com.xddcodec.fs.storage.plugin.core.IStorageOperationService;
import com.xddcodec.fs.storage.plugin.core.context.StoragePlatformContextHolder;
import com.xddcodec.fs.system.domain.SysUser;
import com.xddcodec.fs.system.domain.SysUserTransferSetting;
import com.xddcodec.fs.system.service.SysUserService;
import com.xddcodec.fs.system.service.SysUserTransferSettingService;
import io.github.linpeilie.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.xddcodec.fs.file.domain.table.FileInfoTableDef.FILE_INFO;
import static com.xddcodec.fs.file.domain.table.FileTransferTaskTableDef.FILE_TRANSFER_TASK;

/**
 * 文件传输任务服务实现
 *
 * @author xddcode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileTransferTaskServiceImpl extends ServiceImpl<FileTransferTaskMapper, FileTransferTask> implements FileTransferTaskService {

    private final Converter converter;
    private final FileInfoService fileInfoService;
    private final FileObjectReferenceService objectReferenceService;
    private final TransferSseService transferSseService;
    private final TransferTaskCacheManager cacheManager;
    private final UploadTaskExceptionHandler exceptionHandler;
    private final DownloadTaskExceptionHandler downloadExceptionHandler;
    @Qualifier("chunkUploadExecutor")
    private final TaskExecutor chunkUploadExecutor;
    @Qualifier("fileMergeExecutor")
    private final TaskExecutor fileMergeExecutor;
    private final StorageServiceFacade storageServiceFacade;
    private final SysUserService sysUserService;
    private final SysUserTransferSettingService userTransferSettingService;
    private final SysOperationLogService operationLogService;
    @Value("${spring.application.name:free-fs}")
    private String applicationName;
    @Value("${fs.folder-download.temp-dir:${java.io.tmpdir}/free-fs-folder-download}")
    private String folderDownloadTempDir;
    private static final long FOLDER_DOWNLOAD_TASK_TTL_MS = 2 * 60 * 60 * 1000L;
    private static final long FOLDER_DOWNLOAD_CLEANUP_INTERVAL_MS = 2 * 60 * 60 * 1000L;
    private static final long FOLDER_DOWNLOAD_CANCEL_WAIT_SECONDS = 5L;
    private static final String FOLDER_DOWNLOAD_TASK_PREFIX = "free-fs-folder-task-";
    private static final String FOLDER_DOWNLOAD_DIRECT_PREFIX = "free-fs-folder-";
    private final Map<String, FolderDownloadTask> folderDownloadTasks = new ConcurrentHashMap<>();
    private final Map<String, DeleteOnCloseInputStream> activeFolderDownloadStreams = new ConcurrentHashMap<>();
    private final Set<Path> activeFolderDownloadPaths = ConcurrentHashMap.newKeySet();

    @Override
    public List<FileTransferTaskVO> getTransferFiles(TransferFilesQry qry) {
        String userId = StpUtil.getLoginIdAsString();
        String workspaceId = WorkspaceContext.getWorkspaceId();
        String storagePlatformSettingId = StoragePlatformContextHolder.getConfigId();
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.where(FILE_TRANSFER_TASK.USER_ID.eq(userId)
                .and(FILE_TRANSFER_TASK.WORKSPACE_ID.eq(workspaceId))
                // 公开文件收集上传由专用页面管理，不能混入普通传输列表。
                .and(FILE_TRANSFER_TASK.COLLECTION_ID.isNull())
                .and(FILE_TRANSFER_TASK.COLLECTION_SUBMISSION_ID.isNull()));
        applyStorageScope(queryWrapper, storagePlatformSettingId);
        applyStatusTypeFilter(queryWrapper, qry == null ? null : qry.getStatusType());
        queryWrapper.orderBy(FILE_TRANSFER_TASK.CREATED_AT.asc());
        List<FileTransferTask> tasks = this.list(queryWrapper);
        List<FileTransferTaskVO> voList = converter.convert(tasks, FileTransferTaskVO.class);

        // 计算并填充进度相关字段
        for (FileTransferTaskVO vo : voList) {
            calculateProgressFields(vo);

            // 检查是否有缓存的完成事件（SSE 推送失败的情况）
            if (vo.getStatus() == TransferTaskStatus.completed) {
                Object completeEvent = cacheManager.getAndRemoveCompleteEvent(vo.getTaskId());
                if (completeEvent != null) {
                    log.info("检测到未推送的完成事件，通过轮询返回: taskId={}", vo.getTaskId());
                    vo.setCompleteEventData(completeEvent);
                }
            }
        }

        return voList;
    }

    /**
     * 计算并填充进度相关字段
     */
    private void calculateProgressFields(FileTransferTaskVO vo) {
        String taskId = vo.getTaskId();

        // 获取已传输字节数（上传和下载都使用相同的缓存键）
        long transferredBytes = cacheManager.getTransferredBytes(taskId);
        vo.setUploadedSize(transferredBytes); // 字段名为 uploadedSize，但对下载任务也表示已下载字节数

        // 计算进度百分比（整数，0-100）
        if (vo.getFileSize() != null && vo.getFileSize() > 0) {
            double progressPercent = (transferredBytes * 100.0) / vo.getFileSize();
            // 四舍五入取整
            int progressInt = (int) Math.round(Math.min(progressPercent, 100.0));
            vo.setProgress(progressInt);
        } else {
            vo.setProgress(0);
        }

        // 计算速度和剩余时间（仅对进行中的任务）
        if (vo.getStatus() != null &&
                (vo.getStatus().name().equals("uploading") || vo.getStatus().name().equals("downloading"))) {

            Long startTime = cacheManager.getStartTime(taskId);
            if (startTime != null && transferredBytes > 0) {
                long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;

                if (elapsedSeconds > 0) {
                    // 计算平均速度 (bytes/s)
                    long speed = transferredBytes / elapsedSeconds;
                    vo.setSpeed(speed);

                    // 计算剩余时间（秒）
                    if (speed > 0 && vo.getFileSize() != null) {
                        long remainingBytes = vo.getFileSize() - transferredBytes;
                        int remainTime = (int) (remainingBytes / speed);
                        vo.setRemainTime(remainTime);
                    } else {
                        vo.setRemainTime(null);
                    }
                } else {
                    vo.setSpeed(0L);
                    vo.setRemainTime(null);
                }
            } else {
                vo.setSpeed(0L);
                vo.setRemainTime(null);
            }
        } else {
            // 非进行中的任务不显示速度和剩余时间
            vo.setSpeed(null);
            vo.setRemainTime(null);
        }
    }

    /**
     * 存储平台切换前创建的任务可能没有 storage_platform_setting_id，保留这些历史任务，
     * 否则它们会在旧版本列表中可见、却无法被清空接口命中。
     */
    private void applyStorageScope(QueryWrapper queryWrapper, String storagePlatformSettingId) {
        if (StringUtils.isEmpty(storagePlatformSettingId)) {
            queryWrapper.and(FILE_TRANSFER_TASK.STORAGE_PLATFORM_SETTING_ID.isNull());
            return;
        }
        queryWrapper.and(FILE_TRANSFER_TASK.STORAGE_PLATFORM_SETTING_ID.eq(storagePlatformSettingId)
                .or(FILE_TRANSFER_TASK.STORAGE_PLATFORM_SETTING_ID.isNull()));
    }

    /**
     * 按传输页面约定的状态类型过滤任务：1 上传中、2 下载中、3 已完成（含失败/取消）。
     */
    private void applyStatusTypeFilter(QueryWrapper queryWrapper, Integer statusType) {
        if (statusType == null) {
            return;
        }

        switch (statusType) {
            case 1 -> queryWrapper.and(FILE_TRANSFER_TASK.TASK_TYPE.eq(TransferTaskType.upload)
                    .and(FILE_TRANSFER_TASK.STATUS.in(
                            TransferTaskStatus.initialized,
                            TransferTaskStatus.checking,
                            TransferTaskStatus.uploading,
                            TransferTaskStatus.paused,
                            TransferTaskStatus.merging)));
            case 2 -> queryWrapper.and(FILE_TRANSFER_TASK.TASK_TYPE.eq(TransferTaskType.download)
                    .and(FILE_TRANSFER_TASK.STATUS.in(
                            TransferTaskStatus.initialized,
                            TransferTaskStatus.checking,
                            TransferTaskStatus.downloading,
                            TransferTaskStatus.paused,
                            TransferTaskStatus.merging)));
            case 3 -> queryWrapper.and(FILE_TRANSFER_TASK.STATUS.in(
                    TransferTaskStatus.completed,
                    TransferTaskStatus.failed,
                    TransferTaskStatus.canceled));
            default -> log.warn("忽略未知的传输状态类型: statusType={}", statusType);
        }
    }

    /**
     * 初始化上传
     *
     * @param cmd 初始化上传命令
     * @return 任务ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String initUpload(InitUploadCmd cmd) {
        String userId = StpUtil.getLoginIdAsString();
        String workspaceId = WorkspaceContext.getWorkspaceId();
        String storagePlatformSettingId = StoragePlatformContextHolder.getConfigId();
        try {
            String taskId = IdUtil.fastSimpleUUID();
            String suffix = FileUtils.extName(cmd.getFileName());
            String tempFileName = IdUtil.fastSimpleUUID() + "." + suffix;
            String objectKey = FileUtils.generateObjectKey(applicationName, userId, tempFileName);
            String displayName = fileInfoService.generateUniqueName(
                    workspaceId,
                    cmd.getParentId(),
                    cmd.getFileName(),
                    false,
                    null,
                    storagePlatformSettingId
            );
            FileTransferTask task = new FileTransferTask();
            task.setTaskId(taskId);
            task.setUserId(userId);
            task.setWorkspaceId(workspaceId);
            task.setParentId(cmd.getParentId());
            task.setFileName(displayName);
            task.setFileSize(cmd.getFileSize());
            task.setSuffix(FileUtils.getSuffix(cmd.getFileName()));
            task.setMimeType(cmd.getMimeType());
            task.setTotalChunks(cmd.getTotalChunks());
            task.setUploadedChunks(0);
            task.setTaskType(TransferTaskType.upload);
            task.setChunkSize(cmd.getChunkSize());
            task.setObjectKey(objectKey);
            task.setStoragePlatformSettingId(storagePlatformSettingId);
            task.setStatus(TransferTaskStatus.initialized); // 初始化状态
            task.setStartTime(LocalDateTime.now());
            this.save(task);
            cacheManager.cacheTask(task);
            cacheManager.recordStartTime(task.getTaskId());

            // 推送初始化成功状态事件
            transferSseService.sendStatusEvent(userId, taskId,
                    TransferTaskStatus.initialized.name(), I18nUtils.getMessage("task.init.success"));

            log.info("初始化上传成功: fileName={}", cmd.getFileName());
            return task.getTaskId();
        } catch (Exception e) {
            log.error("初始化上传失败: fileName={}", cmd.getFileName(), e);
            throw new StorageOperationException(I18nUtils.getMessage("task.init.failed", new Object[]{e.getMessage()}), e);
        }
    }

    @Override
    public CheckUploadResultVO checkUpload(CheckUploadCmd cmd) {
        String userId = StpUtil.getLoginIdAsString();
        String storagePlatformSettingId = StoragePlatformContextHolder.getConfigId();
        String taskId = cmd.getTaskId();
        // 获取任务
        FileTransferTask task = null;
        try {
            task = getAuthorizedTask(taskId);
            if (!TransferTaskStatus.initialized.equals(task.getStatus())) {
                throw new BusinessException(I18nUtils.getMessage("task.status.incorrect", new Object[]{task.getStatus()}));
            }
            updateTaskStatus(task, TransferTaskStatus.checking);

            transferSseService.sendStatusEvent(userId, taskId,
                    TransferTaskStatus.checking.name(), I18nUtils.getMessage("task.checking"));
            // 相同存储配置中的相同内容全局复用；回收站记录也属于有效引用。
            try (FileObjectReferenceService.ReferenceLock ignored =
                         objectReferenceService.acquireContentLock(
                                 storagePlatformSettingId,
                                 cmd.getFileMd5(),
                                 task.getFileSize())) {
                FileInfo existFile = objectReferenceService.findReusableFile(
                        cmd.getFileMd5(), task.getFileSize(), storagePlatformSettingId);
                if (existFile != null) {
                    try (FileObjectReferenceService.ReferenceLock objectLock =
                                 objectReferenceService.acquireObjectLock(
                                         existFile.getStoragePlatformSettingId(), existFile.getObjectKey())) {
                        return handleQuickUpload(task, existFile, cmd.getFileMd5(), storagePlatformSettingId);
                    }
                }

                // 0 字节文件不需要分片上传，但仍在内容锁内创建，避免并发产生重复对象。
                if (task.getFileSize() == null || task.getFileSize() == 0) {
                    log.info("检测到空文件上传，直接执行快速完成逻辑: taskId={}", taskId);
                    return handleEmptyFileUpload(task, cmd.getFileMd5(), storagePlatformSettingId);
                }
            }
            // 不是秒传，需要正常上传
            // 调用存储插件初始化分片上传
            IStorageOperationService storageService = storageServiceFacade.getStorageService(storagePlatformSettingId);
            String uploadId = storageService.initiateMultipartUpload(task.getObjectKey(), task.getMimeType());
            // 更新任务信息
            task.setFileMd5(cmd.getFileMd5());
            task.setUploadId(uploadId);

            updateTaskStatus(task, TransferTaskStatus.uploading);

            // 推送可以开始上传状态事件
            transferSseService.sendStatusEvent(userId, taskId,
                    TransferTaskStatus.uploading.name(), I18nUtils.getMessage("task.check.complete"));

            return CheckUploadResultVO.builder()
                    .isQuickUpload(false)
                    .taskId(taskId)
                    .uploadId(uploadId)
                    .message(I18nUtils.getMessage("task.check.complete"))
                    .build();
        } catch (Exception e) {
            log.error("文件校验失败: taskId={}", taskId, e);
            exceptionHandler.handleTaskFailed(taskId, I18nUtils.getMessage("task.check.failed", new Object[]{e.getMessage()}), e);
            throw new StorageOperationException(I18nUtils.getMessage("task.check.failed", new Object[]{e.getMessage()}), e);
        }
    }

    /**
     * 处理 0 字节文件的特殊逻辑
     */
    private CheckUploadResultVO handleEmptyFileUpload(FileTransferTask task, String fileMd5, String storagePlatformSettingId) {
        String taskId = task.getTaskId();
        IStorageOperationService storageService = storageServiceFacade.getStorageService(storagePlatformSettingId);
        try {
            // 既然是空文件，直接通过简单上传接口上传一个空的 InputStream
            storageService.uploadFile(new ByteArrayInputStream(new byte[0]), task.getObjectKey());

            // 复用秒传的后续逻辑：创建 FileInfo 并更新任务状态
            // 构造一个临时的 FileInfo 对象用于 handleQuickUpload
            FileInfo emptyFileInfo = new FileInfo();
            emptyFileInfo.setObjectKey(task.getObjectKey());

            return handleQuickUpload(task, emptyFileInfo, fileMd5, storagePlatformSettingId);
        } catch (Exception e) {
            log.error("空文件处理失败: taskId={}", taskId, e);
            throw new StorageOperationException(I18nUtils.getMessage("task.empty.file.failed"), e);
        }
    }

    /**
     * 处理秒传
     */
    protected CheckUploadResultVO handleQuickUpload(FileTransferTask task,
                                                    FileInfo existFile,
                                                    String fileMd5, String storagePlatformSettingId) {
        String taskId = task.getTaskId();

        try {
            // 创建新的文件记录（引用相同的 objectKey）
            String fileId = IdUtil.fastSimpleUUID();
            LocalDateTime now = LocalDateTime.now();
            String displayName = fileInfoService.generateUniqueName(
                    task.getWorkspaceId(),
                    task.getParentId(),
                    task.getFileName(),
                    false,
                    null,
                    storagePlatformSettingId
            );
            FileInfo newFileInfo = new FileInfo();
            newFileInfo.setId(fileId);
            newFileInfo.setObjectKey(existFile.getObjectKey());
            newFileInfo.setOriginalName(task.getFileName());
            newFileInfo.setDisplayName(displayName);
            newFileInfo.setSuffix(task.getSuffix());
            newFileInfo.setSize(task.getFileSize());
            newFileInfo.setMimeType(task.getMimeType());
            newFileInfo.setIsDir(false);
            newFileInfo.setParentId(task.getParentId());
            newFileInfo.setWorkspaceId(task.getWorkspaceId());
            newFileInfo.setUserId(task.getUserId());
            newFileInfo.setContentMd5(fileMd5);
            newFileInfo.setStoragePlatformSettingId(task.getStoragePlatformSettingId());
            newFileInfo.setUploadTime(now);
            newFileInfo.setUpdateTime(now);
            newFileInfo.setIsDeleted(false);

            fileInfoService.save(newFileInfo);

            operationLogService.recordSuccessAs(
                    task.getWorkspaceId(),
                    task.getUserId(),
                    resolveOperatorName(task.getUserId()),
                    OperationType.UPLOAD,
                    "上传文件（秒传）",
                    "FILE",
                    newFileInfo.getId(),
                    newFileInfo.getDisplayName(),
                    "文件大小: " + FileUtils.formatFileSize(newFileInfo.getSize())
            );

            // 更新任务状态为已完成
            task.setFileMd5(fileMd5);
            task.setUploadedChunks(task.getTotalChunks()); // 标记为全部完成
            task.setStatus(TransferTaskStatus.completed);
            task.setCompleteTime(now);
            this.updateById(task);

            // 清理缓存
            cacheManager.cleanTask(taskId);

            // 推送完成事件
            transferSseService.sendCompleteEvent(task.getUserId(), taskId, fileId,
                    displayName, task.getFileSize());

            log.info("秒传成功: taskId={}, newFileId={}, refObjectKey={}",
                    taskId, fileId, existFile.getObjectKey());

            return CheckUploadResultVO.builder()
                    .isQuickUpload(true)
                    .taskId(taskId)
                    .fileId(fileId)
                    .message(I18nUtils.getMessage("task.quick.upload.success"))
                    .build();
        } catch (Exception e) {
            log.error("秒传处理失败: taskId={}", taskId, e);
            throw new StorageOperationException(I18nUtils.getMessage("task.quick.upload.failed", new Object[]{e.getMessage()}), e);
        }
    }

    /**
     * 上传分片
     *
     * @param fileBytes 分片文件字节数组
     * @param cmd       上传分片命令
     */
    @Override
    public void uploadChunk(byte[] fileBytes, UploadChunkCmd cmd) {
        String taskId = cmd.getTaskId();
        Integer chunkIndex = cmd.getChunkIndex();
        getAuthorizedTask(taskId);
        // 异步上传分片
        CompletableFuture.runAsync(() -> {
            try {
                doUploadChunk(fileBytes, cmd);

                // 检查是否所有分片都已上传完成
                checkAndAutoMerge(taskId);
            } catch (Exception e) {
                log.error("分片上传失败: taskId={}, chunkIndex={}", taskId, cmd.getChunkIndex(), e);
                exceptionHandler.handleChunkUploadFailed(taskId, chunkIndex, e.getMessage(), e);
            }
        }, chunkUploadExecutor);
    }

    /**
     * 检查并自动触发合并（当所有分片上传完成时）
     */
    private void checkAndAutoMerge(String taskId) {
        try {
            FileTransferTask task = getTaskFromCacheOrDB(taskId);

            // 只有 uploading 状态才检查
            if (!TransferTaskStatus.uploading.equals(task.getStatus())) {
                return;
            }

            Integer uploadedCount = cacheManager.getTransferredChunks(taskId);

            // 所有分片都上传完成
            if (uploadedCount.equals(task.getTotalChunks())) {
                // 使用分布式锁防止并发检查导致重复触发合并
                String lockKey = "merge:lock:" + taskId;
                boolean locked = cacheManager.tryLock(lockKey, 300); // 5分钟锁

                if (!locked) {
                    log.debug("合并任务已被其他线程触发，跳过: taskId={}", taskId);
                    return;
                }

                try {
                    // 再次检查状态（双重检查，防止状态已变更）
                    task = getTaskFromCacheOrDB(taskId);
                    if (!TransferTaskStatus.uploading.equals(task.getStatus())) {
                        log.debug("任务状态已变更，跳过合并: taskId={}, status={}", taskId, task.getStatus());
                        return;
                    }

                    log.info("所有分片上传完成，触发自动合并: taskId={}", taskId);

                    // 异步执行合并，避免阻塞上传线程
                    // 注意：锁会在合并完成后由合并任务自己释放
                    CompletableFuture.runAsync(() -> {
                        try {
                            doMergeChunks(taskId);
                        } catch (Exception e) {
                            log.error("自动合并失败: taskId={}", taskId, e);
                            exceptionHandler.handleTaskFailed(taskId, "文件合并失败: " + e.getMessage(), e);
                        } finally {
                            // 合并完成后释放锁
                            cacheManager.releaseLock(lockKey);
                        }
                    }, fileMergeExecutor);
                } catch (Exception e) {
                    // 如果提交异步任务失败，需要释放锁
                    cacheManager.releaseLock(lockKey);
                    throw e;
                }
            }
        } catch (Exception e) {
            log.error("检查自动合并失败: taskId={}", taskId, e);
            // 不抛出异常，避免影响分片上传
        }
    }

    /**
     * 上传分片
     */
    private void doUploadChunk(byte[] fileBytes, UploadChunkCmd cmd) throws IOException {
        String taskId = cmd.getTaskId();
        Integer chunkIndex = cmd.getChunkIndex();
        FileTransferTask task = getTaskFromCacheOrDB(taskId);
        if (task.getStatus() == TransferTaskStatus.canceled) {
            log.info("任务已取消，停止上传: taskId={}, chunkIndex={}", taskId, chunkIndex);
            return;
        }
        if (task.getStatus() == TransferTaskStatus.paused) {
            log.info("任务已暂停，停止上传: taskId={}, chunkIndex={}", taskId, chunkIndex);
            return;
        }
        if (!TransferTaskStatus.uploading.equals(task.getStatus())) {
            throw new BusinessException(I18nUtils.getMessage("task.status.incorrect.expected", new Object[]{task.getStatus()}));
        }
        // 检查分片是否已存在（避免重复上传）
        if (cacheManager.isChunkTransferred(taskId, chunkIndex)) {
            log.info("分片已存在，跳过上传: taskId={}, chunkIndex={}", taskId, chunkIndex);
            return;
        }

        IStorageOperationService storageService =
                storageServiceFacade.getStorageService(task.getStoragePlatformSettingId());

        String eTag;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(fileBytes)) {
            eTag = storageService.uploadPart(
                    task.getObjectKey(),
                    task.getUploadId(),
                    chunkIndex,
                    fileBytes.length,
                    bis);
        }
        cacheManager.addTransferredChunk(taskId, chunkIndex, eTag);
        cacheManager.recordTransferredBytes(taskId, fileBytes.length);

        // 推送进度事件
        Integer uploadedChunks = cacheManager.getTransferredChunks(taskId);
        long uploadedBytes = cacheManager.getTransferredBytes(taskId);
        transferSseService.sendProgressEvent(task.getUserId(), taskId,
                uploadedBytes, task.getFileSize(), uploadedChunks, task.getTotalChunks());

        log.info("分片上传成功: taskId={}, chunkIndex={}, progress={}/{}",
                taskId, chunkIndex, uploadedChunks, task.getTotalChunks());
    }

    @Override
    public void pauseTransfer(String taskId) {
        FileTransferTask task = null;
        try {
            task = getAuthorizedTask(taskId);
            TransferTaskStatus currentStatus = task.getStatus();

            // 验证当前状态是否支持暂停（上传或下载中）
            if (!TransferTaskStatus.uploading.equals(currentStatus)
                    && !TransferTaskStatus.downloading.equals(currentStatus)) {
                throw new BusinessException(I18nUtils.getMessage("task.status.not.support.pause", new Object[]{currentStatus}));
            }

            // 验证状态转换合法性
            validateStateTransition(currentStatus, TransferTaskStatus.paused);

            // 更新数据库状态
            updateTaskStatus(task, TransferTaskStatus.paused);

            // 推送暂停状态事件
            String taskTypeDesc = task.getTaskType() == TransferTaskType.upload ? "上传" : "下载";
            transferSseService.sendStatusEvent(task.getUserId(), taskId,
                    TransferTaskStatus.paused.name(), taskTypeDesc + "任务已暂停");

            log.info("暂停{}任务: taskId={}, taskType={}", taskTypeDesc, taskId, task.getTaskType());
        } catch (Exception e) {
            log.error("暂停失败: taskId={}", taskId, e);
            if (task != null) {
                String userFriendlyMsg = ErrorMessageUtils.extractUserFriendlyMessage(e);
                transferSseService.sendErrorEvent(task.getUserId(), taskId,
                        "PAUSE_FAILED", I18nUtils.getMessage("transfer.pause.failed", new Object[]{userFriendlyMsg}));
            }
            throw new StorageOperationException(I18nUtils.getMessage("transfer.pause.failed", new Object[]{e.getMessage()}), e);
        }
    }

    @Override
    public void resumeTransfer(String taskId) {
        FileTransferTask task = null;
        try {
            task = getAuthorizedTask(taskId);
            TransferTaskStatus currentStatus = task.getStatus();

            // 验证当前状态是否支持恢复
            if (!TransferTaskStatus.paused.equals(currentStatus)) {
                throw new BusinessException(I18nUtils.getMessage("task.status.not.support.resume", new Object[]{currentStatus}));
            }

            // 根据任务类型确定目标状态
            TransferTaskStatus newStatus = task.getTaskType() == TransferTaskType.upload
                    ? TransferTaskStatus.uploading
                    : TransferTaskStatus.downloading;

            // 验证状态转换合法性
            validateStateTransition(currentStatus, newStatus);

            // 更新任务状态
            updateTaskStatus(task, newStatus);

            // 获取已传输的分片信息（上传任务使用 transferredChunkList，下载任务使用 downloadedChunks）
            int transferredCount;
            if (task.getTaskType() == TransferTaskType.upload) {
                Map<Integer, String> transferredChunks = cacheManager.getTransferredChunkList(taskId);
                transferredCount = transferredChunks.size();
            } else {
                Set<Integer> downloadedChunks = getDownloadedChunks(taskId);
                transferredCount = downloadedChunks.size();
            }

            // 推送恢复状态事件
            String taskTypeDesc = task.getTaskType() == TransferTaskType.upload ? "上传" : "下载";
            transferSseService.sendStatusEvent(task.getUserId(), taskId,
                    newStatus.name(), taskTypeDesc + "任务已恢复");

            log.info("继续{}任务成功: taskId={}, taskType={}, transferredChunks={}/{}",
                    taskTypeDesc, taskId, task.getTaskType(), transferredCount, task.getTotalChunks());
        } catch (Exception e) {
            log.error("继续任务失败: taskId={}", taskId, e);
            if (task != null) {
                String userFriendlyMsg = ErrorMessageUtils.extractUserFriendlyMessage(e);
                transferSseService.sendErrorEvent(task.getUserId(), taskId,
                        "RESUME_FAILED", I18nUtils.getMessage("transfer.resume.failed", new Object[]{userFriendlyMsg}));
            }
            throw new StorageOperationException(I18nUtils.getMessage("transfer.resume.failed", new Object[]{e.getMessage()}), e);
        }
    }

    @Override
    public Set<Integer> getUploadedChunks(String taskId) {
        getAuthorizedTask(taskId);
        Map<Integer, String> chunks = cacheManager.getTransferredChunkList(taskId);
        return chunks.keySet();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTransfer(String taskId) {
        FileTransferTask task = null;
        try {
            task = getAuthorizedTask(taskId);
            TransferTaskStatus currentStatus = task.getStatus();

            // 检查任务状态是否可以取消
            if (TransferTaskStatus.completed.equals(currentStatus)) {
                throw new BusinessException(I18nUtils.getMessage("task.completed.cannot.cancel"));
            }

            // 验证状态转换合法性
            validateStateTransition(currentStatus, TransferTaskStatus.canceled);

            // 推送取消中状态事件
            String taskTypeDesc = task.getTaskType() == TransferTaskType.upload ? "上传" : "下载";
            transferSseService.sendStatusEvent(task.getUserId(), taskId,
                    "cancelling", "正在取消" + taskTypeDesc + "任务");

            // 修改状态为已取消
            cacheManager.updateTaskStatus(taskId, TransferTaskStatus.canceled);

            // 短暂延迟，确保前端收到消息并停止传输
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 如果是上传任务且已经初始化了分片上传，需要中止分片上传
            if (TransferTaskType.upload.equals(task.getTaskType())
                    && task.getUploadId() != null
                    && !task.getUploadId().isEmpty()) {
                try {
                    IStorageOperationService storageService =
                            storageServiceFacade.getStorageService(task.getStoragePlatformSettingId());

                    // 中止分片上传，清理存储端的临时数据
                    storageService.abortMultipartUpload(task.getObjectKey(), task.getUploadId());
                    log.info("已中止分片上传: taskId={}, uploadId={}", taskId, task.getUploadId());
                } catch (Exception e) {
                    log.error("中止分片上传失败: taskId={}, uploadId={}", taskId, task.getUploadId(), e);
                }
            }

            // 删除任务记录
            this.removeById(task.getId());

            // 清理缓存（包括下载任务的进度记录）
            cacheManager.cleanTask(taskId);

            // 推送已取消状态事件
            transferSseService.sendStatusEvent(task.getUserId(), taskId,
                    TransferTaskStatus.canceled.name(), taskTypeDesc + "任务已取消");

            log.info("取消{}任务成功: taskId={}, taskType={}", taskTypeDesc, taskId, task.getTaskType());
        } catch (Exception e) {
            log.error("取消传输任务异常: taskId={}", taskId, e);
            if (task != null) {
                String userFriendlyMsg = ErrorMessageUtils.extractUserFriendlyMessage(e);
                transferSseService.sendErrorEvent(task.getUserId(), taskId,
                        "CANCEL_FAILED", I18nUtils.getMessage("transfer.cancel.failed", new Object[]{userFriendlyMsg}));
            }
            throw new StorageOperationException(I18nUtils.getMessage("transfer.cancel.failed", new Object[]{e.getMessage()}), e);
        }
    }

    @Override
    @Transactional
    public FileInfo mergeChunks(String taskId) {
        getAuthorizedTask(taskId);
        return doMergeChunks(taskId);
    }

    public FileInfo doMergeChunks(String taskId) {
        FileTransferTask task = null;
        try {
            log.info("开始合并文件: taskId={}", taskId);
            task = getByTaskId(taskId);
            if (task == null) {
                throw new StorageOperationException(I18nUtils.getMessage("task.not.exist", new Object[]{taskId}));
            }

            // 验证当前状态是否允许合并
            if (!TransferTaskStatus.uploading.equals(task.getStatus())) {
                throw new BusinessException(I18nUtils.getMessage("task.status.incorrect", new Object[]{task.getStatus()}));
            }

            Integer uploadedCount = cacheManager.getTransferredChunks(taskId);
            if (!uploadedCount.equals(task.getTotalChunks())) {
                log.error("分片未全部上传，拒绝合并: taskId={}, uploaded={}, total={}",
                        taskId, uploadedCount, task.getTotalChunks());
                throw new StorageOperationException(
                        String.format("分片不完整：已上传 %d/%d", uploadedCount, task.getTotalChunks())
                );
            }

            log.info("所有分片上传完成，开始合并: taskId={}, totalChunks={}", taskId, task.getTotalChunks());

            // 更新状态为 merging
            updateTaskStatus(task, TransferTaskStatus.merging);

            // 推送 merging 状态事件
            transferSseService.sendStatusEvent(task.getUserId(), taskId,
                    TransferTaskStatus.merging.name(), "正在合并分片");

            log.info("状态已更新为 merging: taskId={}", taskId);
            IStorageOperationService storageService = storageServiceFacade.getStorageService(task.getStoragePlatformSettingId());
            Map<Integer, String> chunkETags = cacheManager.getTransferredChunkList(taskId);

            // 验证所有分片的 ETag 都存在
            log.info("验证分片ETag完整性: taskId={}, totalChunks={}, cachedChunks={}",
                    taskId, task.getTotalChunks(), chunkETags.size());

            if (chunkETags.size() != task.getTotalChunks()) {
                log.error("缓存的分片数量与总数不匹配: taskId={}, cached={}, expected={}",
                        taskId, chunkETags.size(), task.getTotalChunks());
                throw new StorageOperationException(
                        String.format("分片数量不匹配：缓存 %d，期望 %d", chunkETags.size(), task.getTotalChunks())
                );
            }

            // 获取存储服务并完成分片合并
            List<Map<String, Object>> partETags = new ArrayList<>();
            for (int i = 0; i < task.getTotalChunks(); i++) {
                String etag = chunkETags.get(i);
                if (etag == null || etag.isEmpty()) {
                    log.error("分片ETag丢失: taskId={}, chunkIndex={}, allETags={}",
                            taskId, i, chunkETags);
                    throw new StorageOperationException(
                            String.format("分片 %d 的 ETag 丢失", i)
                    );
                }
                Map<String, Object> partInfo = new HashMap<>();
                partInfo.put("partNumber", i);
                partInfo.put("eTag", etag);
                partETags.add(partInfo);
            }

            log.info("分片ETag验证通过，准备合并: taskId={}, partCount={}", taskId, partETags.size());
            storageService.completeMultipartUpload(
                    task.getObjectKey(),
                    task.getUploadId(),
                    partETags
            );

            LocalDateTime completeTime = LocalDateTime.now();
            String uploadedObjectKey = task.getObjectKey();
            FileInfo fileInfo;

            // 合并完成后再次去重，解决多个相同文件并发上传时都未命中秒传的问题。
            try (FileObjectReferenceService.ReferenceLock ignored =
                         objectReferenceService.acquireContentLock(
                                 task.getStoragePlatformSettingId(),
                                 task.getFileMd5(),
                                 task.getFileSize())) {
                FileInfo reusableFile = objectReferenceService.findReusableFile(
                        task.getFileMd5(), task.getFileSize(), task.getStoragePlatformSettingId());
                if (reusableFile != null) {
                    try (FileObjectReferenceService.ReferenceLock objectLock =
                                 objectReferenceService.acquireObjectLock(
                                         reusableFile.getStoragePlatformSettingId(), reusableFile.getObjectKey())) {
                        fileInfo = buildUploadedFileInfo(task, reusableFile.getObjectKey(), completeTime);
                        fileInfoService.save(fileInfo);
                    }
                } else {
                    fileInfo = buildUploadedFileInfo(task, uploadedObjectKey, completeTime);
                    fileInfoService.save(fileInfo);
                }
            }

            if (!Objects.equals(uploadedObjectKey, fileInfo.getObjectKey())) {
                try {
                    storageService.deleteFile(uploadedObjectKey);
                    log.info("并发上传去重成功，删除重复物理对象: taskId={}, objectKey={}",
                            taskId, uploadedObjectKey);
                } catch (Exception deleteError) {
                    // 数据库已经引用已有对象，此处失败只会留下孤立对象，不会造成文件丢失。
                    log.warn("删除并发上传产生的重复对象失败: taskId={}, objectKey={}",
                            taskId, uploadedObjectKey, deleteError);
                }
            }

            task.setStatus(TransferTaskStatus.completed);
            task.setUploadedChunks(uploadedCount);
            task.setCompleteTime(completeTime);
            this.updateById(task);

            operationLogService.recordSuccessAs(
                    task.getWorkspaceId(),
                    task.getUserId(),
                    resolveOperatorName(task.getUserId()),
                    OperationType.UPLOAD,
                    "上传文件",
                    "FILE",
                    fileInfo.getId(),
                    fileInfo.getDisplayName(),
                    "文件大小: " + FileUtils.formatFileSize(fileInfo.getSize())
            );

            cacheManager.cleanTask(taskId);

            // 推送完成事件
            transferSseService.sendCompleteEvent(task.getUserId(), taskId,
                    fileInfo.getId(), fileInfo.getOriginalName(), fileInfo.getSize());

            log.info("分片合并成功: taskId={}, fileId={}, fileName={}", taskId, fileInfo.getId(), fileInfo.getOriginalName());

            return fileInfo;

        } catch (Exception e) {
            log.error("分片合并失败: taskId={}", taskId, e);

            // 推送错误事件
            if (task != null) {
                String userFriendlyMsg = ErrorMessageUtils.extractUserFriendlyMessage(e);
                transferSseService.sendErrorEvent(task.getUserId(), taskId,
                        "MERGE_FAILED", I18nUtils.getMessage("task.merge.failed", new Object[]{userFriendlyMsg}));
            }

            throw new StorageOperationException(I18nUtils.getMessage("task.merge.failed", new Object[]{e.getMessage()}), e);
        }
    }

    private FileInfo buildUploadedFileInfo(FileTransferTask task, String objectKey, LocalDateTime completeTime) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setId(IdUtil.fastSimpleUUID());
        fileInfo.setObjectKey(objectKey);
        fileInfo.setOriginalName(task.getFileName());
        fileInfo.setDisplayName(task.getFileName());
        fileInfo.setSuffix(task.getSuffix());
        fileInfo.setSize(task.getFileSize());
        fileInfo.setMimeType(task.getMimeType());
        fileInfo.setIsDir(false);
        fileInfo.setParentId(task.getParentId());
        fileInfo.setWorkspaceId(task.getWorkspaceId());
        fileInfo.setUserId(task.getUserId());
        fileInfo.setContentMd5(task.getFileMd5());
        fileInfo.setStoragePlatformSettingId(task.getStoragePlatformSettingId());
        fileInfo.setUploadTime(completeTime);
        fileInfo.setUpdateTime(completeTime);
        fileInfo.setIsDeleted(false);
        return fileInfo;
    }

    /**
     * 根据任务ID获取任务信息
     *
     * @param taskId 任务ID
     */
    private FileTransferTask getByTaskId(String taskId) {
        return this.getOne(
                new QueryWrapper().where(FILE_TRANSFER_TASK.TASK_ID.eq(taskId)
                )
        );
    }

    private FileTransferTask getTaskFromCacheOrDB(String taskId) {
        FileTransferTask task = cacheManager.getTaskFromCache(taskId);
        if (task == null) {
            task = this.getOne(
                    QueryWrapper.create().where(FileTransferTask::getTaskId).eq(taskId)
            );
            if (task == null) {
                throw new BusinessException(I18nUtils.getMessage("task.not.exist", new Object[]{taskId}));
            }
            // 缓存到 Redis
            cacheManager.cacheTask(task);
        }
        return task;
    }

    private FileTransferTask getAuthorizedTask(String taskId) {
        if (StringUtils.isBlank(taskId)) {
            throw new BusinessException(I18nUtils.getMessage("task.id.empty"));
        }
        FileTransferTask task = getTaskFromCacheOrDB(taskId);
        String userId = StpUtil.getLoginIdAsString();
        String workspaceId = WorkspaceContext.getWorkspaceId();
        if (!Objects.equals(userId, task.getUserId()) || !Objects.equals(workspaceId, task.getWorkspaceId())) {
            throw new BusinessException(I18nUtils.getMessage("file.no.permission.download"));
        }
        return task;
    }

    /**
     * 验证状态转换是否合法
     *
     * @param currentStatus 当前状态
     * @param newStatus     目标状态
     * @throws BusinessException 如果状态转换不合法
     */
    private void validateStateTransition(TransferTaskStatus currentStatus, TransferTaskStatus newStatus) {
        // 如果状态相同，允许（幂等操作）
        if (currentStatus == newStatus) {
            return;
        }

        // 根据状态机规则验证转换合法性
        boolean isValid = switch (currentStatus) {
            case initialized ->
                // initialized 可以转换到: checking, failed, canceled
                    newStatus == TransferTaskStatus.checking
                            || newStatus == TransferTaskStatus.failed
                            || newStatus == TransferTaskStatus.canceled;
            case checking ->
                // checking 可以转换到: uploading, completed, failed, canceled
                    newStatus == TransferTaskStatus.uploading
                            || newStatus == TransferTaskStatus.completed
                            || newStatus == TransferTaskStatus.failed
                            || newStatus == TransferTaskStatus.canceled;
            case uploading ->
                // uploading 可以转换到: paused, merging, failed, canceled
                    newStatus == TransferTaskStatus.paused
                            || newStatus == TransferTaskStatus.merging
                            || newStatus == TransferTaskStatus.failed
                            || newStatus == TransferTaskStatus.canceled;
            case paused ->
                // paused 可以转换到: uploading, downloading, canceled
                    newStatus == TransferTaskStatus.uploading
                            || newStatus == TransferTaskStatus.downloading
                            || newStatus == TransferTaskStatus.canceled;
            case merging ->
                // merging 可以转换到: completed, failed
                    newStatus == TransferTaskStatus.completed
                            || newStatus == TransferTaskStatus.failed;
            case failed ->
                // failed 可以转换到: initialized (重试)
                    newStatus == TransferTaskStatus.initialized;
            case downloading ->
                // downloading 可以转换到: paused, completed, failed, canceled
                    newStatus == TransferTaskStatus.paused
                            || newStatus == TransferTaskStatus.completed
                            || newStatus == TransferTaskStatus.failed
                            || newStatus == TransferTaskStatus.canceled;
            case completed, canceled ->
                // completed 和 canceled 是终态，不允许转换
                    false;
            default -> false;
        };

        if (!isValid) {
            throw new BusinessException(
                    String.format("非法的状态转换: %s -> %s", currentStatus, newStatus)
            );
        }
    }

    /**
     * 更新任务状态（数据库 + 缓存）
     */
    private void updateTaskStatus(FileTransferTask task, TransferTaskStatus newStatus) {
        // 验证状态转换合法性
        validateStateTransition(task.getStatus(), newStatus);

        task.setStatus(newStatus);
        task.setUpdatedAt(LocalDateTime.now());
        this.updateById(task);
        cacheManager.cacheTask(task);
        cacheManager.updateTaskStatus(task.getTaskId(), newStatus);
    }

    /**
     * 计算分片总数
     *
     * @param fileSize  文件大小（字节）
     * @param chunkSize 分片大小（字节）
     * @return 分片总数
     */
    private int calculateTotalChunks(Long fileSize, Long chunkSize) {
        if (fileSize == null || fileSize <= 0) {
            throw new BusinessException(I18nUtils.getMessage("task.file.size.invalid"));
        }

        if (chunkSize == null || chunkSize <= 0) {
            throw new BusinessException(I18nUtils.getMessage("task.chunk.size.invalid"));
        }
        // 向上取整
        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);

        log.debug("计算分片数: fileSize={}, chunkSize={}, totalChunks={}",
                fileSize, chunkSize, totalChunks);

        return totalChunks;
    }

    @Override
    public void clearTransfers() {
        String userId = StpUtil.getLoginIdAsString();
        String workspaceId = WorkspaceContext.getWorkspaceId();
        String storagePlatformSettingId = StoragePlatformContextHolder.getConfigId();

        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.where(FILE_TRANSFER_TASK.STATUS.in(
                        TransferTaskStatus.completed,
                        TransferTaskStatus.failed,
                        TransferTaskStatus.canceled))
                .and(FILE_TRANSFER_TASK.USER_ID.eq(userId))
                .and(FILE_TRANSFER_TASK.WORKSPACE_ID.eq(workspaceId))
                // 文件收集任务由收集记录管理，不能被普通传输页清理。
                .and(FILE_TRANSFER_TASK.COLLECTION_ID.isNull())
                .and(FILE_TRANSFER_TASK.COLLECTION_SUBMISSION_ID.isNull());
        applyStorageScope(queryWrapper, storagePlatformSettingId);
        List<FileTransferTask> tasks = this.list(queryWrapper);

        if (tasks.isEmpty()) {
            return;
        }

        this.remove(queryWrapper);

        List<String> taskIds = tasks.stream()
                .map(FileTransferTask::getTaskId)
                .collect(Collectors.toList());

        //清除缓存
        cacheManager.cleanTasks(taskIds);
    }

    /**
     * 合并任务通常在异步线程完成，不能依赖当前请求线程的 Sa-Token 会话；
     * 因此按任务中的用户 ID 查询稳定的用户名，避免把 ULID 当作操作人名称。
     */
    private String resolveOperatorName(String userId) {
        if (StringUtils.isEmpty(userId)) {
            return userId;
        }
        try {
            SysUser user = sysUserService.getById(userId);
            if (user != null) {
                if (StringUtils.isNotEmpty(user.getUsername())) {
                    return user.getUsername();
                }
                if (StringUtils.isNotEmpty(user.getNickname())) {
                    return user.getNickname();
                }
            }
        } catch (Exception e) {
            log.warn("查询操作日志用户名失败: userId={}", userId, e);
        }
        return userId;
    }

    @Override
    public FileDownloadVO downloadFile(String fileId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        FileInfo fileInfo = fileInfoService.getById(fileId);
        if (fileInfo == null) {
            throw new BusinessException(I18nUtils.getMessage("file.download.failed.not.exist"));
        }
        if (!workspaceId.equals(fileInfo.getWorkspaceId())) {
            throw new BusinessException(I18nUtils.getMessage("file.no.permission.download"));
        }
        if (Boolean.TRUE.equals(fileInfo.getIsDir())) {
            return downloadDirectory(fileInfo);
        }
        IStorageOperationService storageService = storageServiceFacade.getStorageService(fileInfo.getStoragePlatformSettingId());
        if (!storageService.isFileExist(fileInfo.getObjectKey())) {
            throw new BusinessException(I18nUtils.getMessage("file.download.failed.not.exist"));
        }
        InputStream inputStream = storageService.downloadFile(fileInfo.getObjectKey());
        InputStreamResource resource = new InputStreamResource(inputStream);
        FileDownloadVO downloadVO = new FileDownloadVO();
        downloadVO.setFileName(fileInfo.getDisplayName());
        downloadVO.setFileSize(fileInfo.getSize());
        downloadVO.setResource(resource);
        return downloadVO;
    }

    @Override
    public FolderDownloadTaskVO createFolderDownloadTask(String folderId) {
        cleanupExpiredFolderDownloadTasks();

        String userId = StpUtil.getLoginIdAsString();
        String workspaceId = WorkspaceContext.getWorkspaceId();
        String storagePlatformSettingId = StoragePlatformContextHolder.getConfigId();

        FolderDownloadTask existingTask = findReusableFolderDownloadTask(userId, workspaceId, folderId);
        if (existingTask != null) {
            return toFolderDownloadTaskVO(existingTask);
        }

        FileInfo folderInfo = fileInfoService.getById(folderId);
        if (folderInfo == null || !Boolean.TRUE.equals(folderInfo.getIsDir()) || Boolean.TRUE.equals(folderInfo.getIsDeleted())) {
            throw new BusinessException("文件夹不存在");
        }
        if (!workspaceId.equals(folderInfo.getWorkspaceId())) {
            throw new BusinessException(I18nUtils.getMessage("file.no.permission.download"));
        }

        FolderDownloadTask task = new FolderDownloadTask();
        task.taskId = IdUtil.fastSimpleUUID();
        task.userId = userId;
        task.workspaceId = workspaceId;
        task.storagePlatformSettingId = storagePlatformSettingId;
        task.folderId = folderInfo.getId();
        task.folderName = folderInfo.getDisplayName();
        task.status = "queued";
        task.progress = 0;
        task.totalFiles = 0;
        task.processedFiles = 0;
        task.totalBytes = 0L;
        task.processedBytes = 0L;
        task.message = "正在创建下载任务";
        task.createdAt = System.currentTimeMillis();
        task.updatedAt = task.createdAt;
        task.lastActivityAt = task.createdAt;
        FutureTask<Void> buildFuture = new FutureTask<>(() -> {
            buildFolderDownloadTask(task, folderInfo);
            return null;
        });
        task.buildFuture = buildFuture;
        folderDownloadTasks.put(task.taskId, task);
        try {
            fileMergeExecutor.execute(buildFuture);
        } catch (RuntimeException e) {
            folderDownloadTasks.remove(task.taskId, task);
            throw e;
        }
        return toFolderDownloadTaskVO(task);
    }

    @Override
    public FolderDownloadTaskVO getFolderDownloadTask(String taskId) {
        cleanupExpiredFolderDownloadTasks();
        return toFolderDownloadTaskVO(getAuthorizedFolderDownloadTask(taskId));
    }

    @Override
    public void cancelFolderDownloadTask(String taskId) {
        FolderDownloadTask task = getAuthorizedFolderDownloadTask(taskId);
        cancelFolderDownloadTaskInternal(task, "用户取消文件夹打包", true);
    }

    @Override
    public FileDownloadVO downloadFolderTaskFile(String taskId) {
        FolderDownloadTask task = getAuthorizedFolderDownloadTask(taskId);
        Path zipPath;
        long zipSize;
        synchronized (task) {
            if (!"completed".equals(task.status)) {
                throw new BusinessException("文件夹仍在打包中，请稍后再试");
            }
            zipPath = task.zipPath;
            if (zipPath == null || !Files.exists(zipPath)) {
                folderDownloadTasks.remove(taskId, task);
                throw new BusinessException("下载文件已过期，请重新下载");
            }
            try {
                zipSize = Files.size(zipPath);
            } catch (IOException e) {
                throw new StorageOperationException("读取文件夹压缩包失败: " + e.getMessage(), e);
            }
            task.activeDownloadCount++;
            task.status = "downloading";
            task.downloadStartedAt = System.currentTimeMillis();
            task.lastActivityAt = task.downloadStartedAt;
        }

        String streamId = IdUtil.fastSimpleUUID();
        try {
            Path activeZipPath = normalizePath(zipPath);
            DeleteOnCloseInputStream managedInputStream = new DeleteOnCloseInputStream(
                    Files.newInputStream(zipPath),
                    zipPath,
                    false,
                    () -> {
                        activeFolderDownloadStreams.remove(streamId);
                        activeFolderDownloadPaths.remove(activeZipPath);
                        releaseFolderDownload(task);
                    }
            );
            activeFolderDownloadStreams.put(streamId, managedInputStream);
            InputStream inputStream = managedInputStream;
            FileDownloadVO downloadVO = new FileDownloadVO();
            downloadVO.setFileName(task.zipFileName);
            downloadVO.setFileSize(zipSize);
            downloadVO.setResource(new InputStreamResource(inputStream));
            return downloadVO;
        } catch (IOException e) {
            activeFolderDownloadStreams.remove(streamId);
            releaseFolderDownload(task);
            throw new StorageOperationException("文件夹下载失败: " + e.getMessage(), e);
        }
    }

    private FolderDownloadTask findReusableFolderDownloadTask(String userId, String workspaceId, String folderId) {
        return folderDownloadTasks.values().stream()
                .filter(task -> userId.equals(task.userId)
                        && workspaceId.equals(task.workspaceId)
                        && folderId.equals(task.folderId)
                        && ("queued".equals(task.status)
                        || "scanning".equals(task.status)
                        || "packing".equals(task.status)
                        || "completed".equals(task.status))
                        && !task.cancelRequested)
                .findFirst()
                .orElse(null);
    }

    private FolderDownloadTask getAuthorizedFolderDownloadTask(String taskId) {
        FolderDownloadTask task = folderDownloadTasks.get(taskId);
        if (task == null) {
            throw new BusinessException("文件夹下载任务不存在或已过期");
        }

        String userId = StpUtil.getLoginIdAsString();
        String workspaceId = WorkspaceContext.getWorkspaceId();
        if (!userId.equals(task.userId) || !workspaceId.equals(task.workspaceId)) {
            throw new BusinessException(I18nUtils.getMessage("file.no.permission.download"));
        }
        return task;
    }

    private void buildFolderDownloadTask(FolderDownloadTask task, FileInfo folderInfo) {
        task.buildStarted = true;
        Path zipPath = null;
        try {
            checkFolderDownloadCancellation(task);
            updateFolderDownloadTask(task, "scanning", 0, "正在统计文件");
            FolderDownloadScanResult scanResult = scanDirectory(folderInfo, new HashSet<>(), task);
            synchronized (task) {
                task.totalFiles = scanResult.totalFiles;
                task.totalBytes = scanResult.totalBytes;
                touchFolderDownloadTask(task);
            }

            zipPath = createFolderDownloadZipPath(FOLDER_DOWNLOAD_TASK_PREFIX);
            synchronized (task) {
                task.zipPath = zipPath;
                task.zipFileName = buildZipFileName(task.folderName);
            }

            updateFolderDownloadTask(task, "packing", 0, "正在打包文件夹");
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipPath))) {
                writeDirectoryToZipWithProgress(folderInfo, sanitizeZipName(folderInfo.getDisplayName()),
                        zipOutputStream, new HashSet<>(), task);
            }

            synchronized (task) {
                checkFolderDownloadCancellation(task);
                task.status = "completed";
                task.progress = 100;
                task.zipSize = Files.size(zipPath);
                task.message = "打包完成，准备下载";
                task.completedAt = System.currentTimeMillis();
                task.updatedAt = task.completedAt;
                task.lastActivityAt = task.completedAt;
            }
            log.info("文件夹打包完成: taskId={}, folderId={}, zipSize={}",
                    task.taskId, task.folderId, task.zipSize);
        } catch (FolderDownloadCanceledException e) {
            log.info("文件夹打包任务已取消: taskId={}, folderId={}", task.taskId, task.folderId);
            markFolderDownloadCanceled(task, e.getMessage());
        } catch (Exception e) {
            if (task.cancelRequested) {
                log.info("文件夹打包任务中断并取消: taskId={}, folderId={}", task.taskId, task.folderId);
                markFolderDownloadCanceled(task, "文件夹打包已取消");
            } else {
                log.error("文件夹打包任务失败: taskId={}, folderId={}", task.taskId, task.folderId, e);
                synchronized (task) {
                    task.status = "failed";
                    task.progress = 0;
                    task.errorMessage = e.getMessage();
                    task.message = "打包失败";
                    task.updatedAt = System.currentTimeMillis();
                    task.lastActivityAt = task.updatedAt;
                    task.completedAt = task.updatedAt;
                }
            }
        } finally {
            if (!"completed".equals(task.status)) {
                deleteFolderDownloadZip(task.zipPath != null ? task.zipPath : zipPath);
            }
            task.currentInputStream = null;
            task.buildFinished.countDown();
            if ("canceled".equals(task.status)) {
                folderDownloadTasks.remove(task.taskId, task);
            }
        }
    }

    private FolderDownloadScanResult scanDirectory(FileInfo dirInfo, Set<String> visitedDirIds,
                                                    FolderDownloadTask task) throws IOException {
        checkFolderDownloadCancellation(task);
        FolderDownloadScanResult result = new FolderDownloadScanResult();
        if (!visitedDirIds.add(dirInfo.getId())) {
            return result;
        }

        for (FileInfo child : listDirectoryChildren(dirInfo)) {
            checkFolderDownloadCancellation(task);
            touchFolderDownloadTask(task);
            if (Boolean.TRUE.equals(child.getIsDir())) {
                FolderDownloadScanResult childResult = scanDirectory(child, visitedDirIds, task);
                result.totalFiles += childResult.totalFiles;
                result.totalBytes += childResult.totalBytes;
            } else {
                result.totalFiles += 1;
                result.totalBytes += child.getSize() == null ? 0L : child.getSize();
            }
        }
        return result;
    }

    private void writeDirectoryToZipWithProgress(FileInfo dirInfo, String dirPath,
                                                 ZipOutputStream zipOutputStream, Set<String> visitedDirIds,
                                                 FolderDownloadTask task) throws IOException {
        checkFolderDownloadCancellation(task);
        if (!visitedDirIds.add(dirInfo.getId())) {
            return;
        }

        String normalizedDirPath = ensureTrailingSlash(dirPath);
        zipOutputStream.putNextEntry(new ZipEntry(normalizedDirPath));
        zipOutputStream.closeEntry();

        for (FileInfo child : listDirectoryChildren(dirInfo)) {
            checkFolderDownloadCancellation(task);
            String childPath = normalizedDirPath + sanitizeZipName(child.getDisplayName());
            if (Boolean.TRUE.equals(child.getIsDir())) {
                writeDirectoryToZipWithProgress(child, childPath, zipOutputStream, visitedDirIds, task);
            } else {
                writeFileToZipWithProgress(child, childPath, zipOutputStream, task);
            }
        }
    }

    private void writeFileToZipWithProgress(FileInfo fileInfo, String zipEntryName,
                                            ZipOutputStream zipOutputStream, FolderDownloadTask task) throws IOException {
        checkFolderDownloadCancellation(task);
        IStorageOperationService storageService = storageServiceFacade.getStorageService(fileInfo.getStoragePlatformSettingId());
        if (!storageService.isFileExist(fileInfo.getObjectKey())) {
            log.warn("文件夹打包时跳过不存在的文件: fileId={}, objectKey={}", fileInfo.getId(), fileInfo.getObjectKey());
            incrementProcessedFile(task);
            return;
        }

        zipOutputStream.putNextEntry(new ZipEntry(zipEntryName));
        byte[] buffer = new byte[64 * 1024];
        InputStream sourceInputStream = storageService.downloadFile(fileInfo.getObjectKey());
        task.currentInputStream = sourceInputStream;
        try (InputStream inputStream = sourceInputStream) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                checkFolderDownloadCancellation(task);
                zipOutputStream.write(buffer, 0, read);
                addProcessedBytes(task, read);
            }
        } finally {
            task.currentInputStream = null;
            zipOutputStream.closeEntry();
            incrementProcessedFile(task);
        }
    }

    private void updateFolderDownloadTask(FolderDownloadTask task, String status, int progress, String message) {
        synchronized (task) {
            if (task.cancelRequested && !"canceled".equals(status)) {
                return;
            }
            task.status = status;
            task.progress = progress;
            task.message = message;
            touchFolderDownloadTask(task);
        }
    }

    private void addProcessedBytes(FolderDownloadTask task, long bytes) {
        synchronized (task) {
            task.processedBytes += bytes;
            updateFolderDownloadProgress(task);
        }
    }

    private void incrementProcessedFile(FolderDownloadTask task) {
        synchronized (task) {
            task.processedFiles += 1;
            updateFolderDownloadProgress(task);
        }
    }

    private void updateFolderDownloadProgress(FolderDownloadTask task) {
        if (task.cancelRequested) {
            return;
        }
        int progress = 0;
        if (task.totalBytes != null && task.totalBytes > 0) {
            progress = (int) Math.floor((task.processedBytes * 100.0) / task.totalBytes);
        } else if (task.totalFiles != null && task.totalFiles > 0) {
            progress = (int) Math.floor((task.processedFiles * 100.0) / task.totalFiles);
        }
        task.progress = Math.max(0, Math.min(99, progress));
        task.message = "正在打包文件夹";
        touchFolderDownloadTask(task);
    }

    private FolderDownloadTaskVO toFolderDownloadTaskVO(FolderDownloadTask task) {
        synchronized (task) {
            FolderDownloadTaskVO vo = new FolderDownloadTaskVO();
            vo.setTaskId(task.taskId);
            vo.setFolderId(task.folderId);
            vo.setFolderName(task.folderName);
            vo.setStatus(task.status);
            vo.setProgress(task.progress);
            vo.setTotalFiles(task.totalFiles);
            vo.setProcessedFiles(task.processedFiles);
            vo.setTotalBytes(task.totalBytes);
            vo.setProcessedBytes(task.processedBytes);
            vo.setZipSize(task.zipSize);
            vo.setMessage(task.message);
            vo.setErrorMessage(task.errorMessage);
            return vo;
        }
    }

    private void checkFolderDownloadCancellation(FolderDownloadTask task) throws FolderDownloadCanceledException {
        if (task.cancelRequested || Thread.currentThread().isInterrupted()) {
            throw new FolderDownloadCanceledException("文件夹打包已取消");
        }
    }

    private void touchFolderDownloadTask(FolderDownloadTask task) {
        synchronized (task) {
            if (task.cancelRequested) {
                return;
            }
            long now = System.currentTimeMillis();
            task.updatedAt = now;
            task.lastActivityAt = now;
        }
    }

    private void markFolderDownloadCanceled(FolderDownloadTask task, String message) {
        synchronized (task) {
            task.cancelRequested = true;
            task.status = "canceled";
            task.message = StringUtils.isEmpty(message) ? "文件夹打包已取消" : message;
            task.errorMessage = null;
            task.updatedAt = System.currentTimeMillis();
            task.lastActivityAt = task.updatedAt;
            task.completedAt = task.updatedAt;
        }
    }

    private boolean cancelFolderDownloadTaskInternal(FolderDownloadTask task, String message,
                                                      boolean rejectAlreadyStartedDownload) {
        Future<?> buildFuture;
        InputStream currentInputStream;
        boolean needsCancellation;
        boolean shouldInterrupt;
        synchronized (task) {
            if ("completed".equals(task.status) || "downloading".equals(task.status)) {
                if (rejectAlreadyStartedDownload) {
                    throw new BusinessException("文件夹已经开始下载，不能取消");
                }
                return false;
            }
            needsCancellation = !"failed".equals(task.status) && !"canceled".equals(task.status);
            shouldInterrupt = !"failed".equals(task.status);
            if (needsCancellation) {
                markFolderDownloadCanceled(task, message);
            }
            buildFuture = task.buildFuture;
            currentInputStream = task.currentInputStream;
        }

        if (shouldInterrupt && currentInputStream != null) {
            try {
                currentInputStream.close();
            } catch (IOException e) {
                log.debug("关闭已取消文件夹打包的输入流失败: taskId={}", task.taskId, e);
            }
        }
        if (shouldInterrupt && buildFuture != null && !buildFuture.isDone()) {
            buildFuture.cancel(true);
        }
        if (!task.buildStarted && (buildFuture == null || buildFuture.isDone())) {
            task.buildFinished.countDown();
        }

        try {
            if (!task.buildFinished.await(FOLDER_DOWNLOAD_CANCEL_WAIT_SECONDS, TimeUnit.SECONDS)) {
                deleteFolderDownloadZip(task.zipPath);
                log.warn("文件夹打包取消后仍在退出，交由后台清理: taskId={}", task.taskId);
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("等待文件夹打包取消被中断: taskId={}", task.taskId);
            return true;
        }

        deleteFolderDownloadZip(task.zipPath);
        folderDownloadTasks.remove(task.taskId, task);
        return true;
    }

    private void releaseFolderDownload(FolderDownloadTask task) {
        boolean finished;
        synchronized (task) {
            if (task.activeDownloadCount > 0) {
                task.activeDownloadCount--;
            }
            task.lastActivityAt = System.currentTimeMillis();
            finished = task.activeDownloadCount == 0;
        }
        if (finished) {
            deleteFolderDownloadZip(task.zipPath);
            folderDownloadTasks.remove(task.taskId, task);
        }
    }

    private boolean isFolderDownloadTaskActive(String status) {
        return "queued".equals(status) || "scanning".equals(status) || "packing".equals(status);
    }

    private Path createFolderDownloadZipPath(String prefix) throws IOException {
        Path tempDirectory = resolveFolderDownloadTempDirectory();
        Files.createDirectories(tempDirectory);
        return Files.createTempFile(tempDirectory, prefix, ".zip");
    }

    private Path resolveFolderDownloadTempDirectory() {
        if (StringUtils.isEmpty(folderDownloadTempDir)) {
            return Path.of(System.getProperty("java.io.tmpdir"), "free-fs-folder-download")
                    .toAbsolutePath().normalize();
        }
        return Path.of(folderDownloadTempDir).toAbsolutePath().normalize();
    }

    private Path normalizePath(Path path) {
        return path == null ? null : path.toAbsolutePath().normalize();
    }

    private void addProtectedPath(Set<Path> protectedPaths, Path path) {
        if (path != null) {
            protectedPaths.add(path);
        }
    }

    private boolean isFolderDownloadZipFile(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".zip")
                && (fileName.startsWith(FOLDER_DOWNLOAD_TASK_PREFIX)
                || fileName.startsWith(FOLDER_DOWNLOAD_DIRECT_PREFIX));
    }

    private boolean isAllowedFolderDownloadTempPath(Path path) {
        Path normalized = normalizePath(path);
        if (normalized == null || !isFolderDownloadZipFile(normalized)) {
            return false;
        }
        Path dedicatedDirectory = resolveFolderDownloadTempDirectory();
        Path legacyDirectory = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        return normalized.startsWith(dedicatedDirectory)
                || (parent != null && parent.equals(legacyDirectory));
    }

    private void deleteFolderDownloadZip(Path path) {
        if (path == null) {
            return;
        }
        if (!isAllowedFolderDownloadTempPath(path)) {
            log.warn("拒绝删除非文件夹下载临时文件: {}", path);
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("删除文件夹下载临时文件失败: {}", path, e);
        }
    }

    private void cleanupStaleActiveFolderDownloadStreams(long now) {
        for (DeleteOnCloseInputStream inputStream : activeFolderDownloadStreams.values()) {
            if (now - inputStream.getLastActivityAt() > FOLDER_DOWNLOAD_TASK_TTL_MS) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    log.warn("关闭超时文件夹下载流失败", e);
                }
            }
        }
    }

    private void cleanupOrphanFolderDownloadFiles(Set<Path> protectedPaths, long now) {
        Set<Path> scanDirectories = new LinkedHashSet<>();
        scanDirectories.add(resolveFolderDownloadTempDirectory());
        scanDirectories.add(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize());

        for (Path directory : scanDirectories) {
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (DirectoryStream<Path> paths = Files.newDirectoryStream(directory, this::isFolderDownloadZipFile)) {
                for (Path path : paths) {
                    Path normalized = normalizePath(path);
                    if (protectedPaths.contains(normalized) || !Files.isRegularFile(normalized)) {
                        continue;
                    }
                    long lastModifiedAt = Files.getLastModifiedTime(normalized).toMillis();
                    if (now - lastModifiedAt > FOLDER_DOWNLOAD_TASK_TTL_MS) {
                        deleteFolderDownloadZip(normalized);
                        log.info("清理孤立文件夹下载临时文件: {}", normalized);
                    }
                }
            } catch (IOException e) {
                log.warn("扫描文件夹下载临时目录失败: {}", directory, e);
            }
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cleanupFolderDownloadTasksOnStartup() {
        log.info("应用启动，开始检查文件夹下载临时文件");
        cleanupExpiredFolderDownloadTasks(true);
    }

    @Scheduled(fixedDelay = FOLDER_DOWNLOAD_CLEANUP_INTERVAL_MS,
            initialDelay = FOLDER_DOWNLOAD_CLEANUP_INTERVAL_MS)
    public void scheduledFolderDownloadCleanup() {
        cleanupExpiredFolderDownloadTasks(true);
    }

    private void cleanupExpiredFolderDownloadTasks() {
        cleanupExpiredFolderDownloadTasks(false);
    }

    private void cleanupExpiredFolderDownloadTasks(boolean scanOrphanFiles) {
        long now = System.currentTimeMillis();
        cleanupStaleActiveFolderDownloadStreams(now);
        Set<Path> protectedPaths = new HashSet<>(activeFolderDownloadPaths);
        activeFolderDownloadStreams.values().forEach(inputStream ->
                addProtectedPath(protectedPaths, normalizePath(inputStream.getPath())));

        for (Map.Entry<String, FolderDownloadTask> entry : folderDownloadTasks.entrySet()) {
            FolderDownloadTask task = entry.getValue();
            boolean shouldCancel = false;
            boolean shouldRemove = false;

            synchronized (task) {
                Path taskPath = normalizePath(task.zipPath);
                boolean buildFinished = task.buildFinished.getCount() == 0;

                if (task.activeDownloadCount > 0 || "downloading".equals(task.status)) {
                    addProtectedPath(protectedPaths, taskPath);
                } else if (isFolderDownloadTaskActive(task.status)) {
                    if (now - task.lastActivityAt > FOLDER_DOWNLOAD_TASK_TTL_MS) {
                        shouldCancel = true;
                    } else {
                        addProtectedPath(protectedPaths, taskPath);
                    }
                } else if ("completed".equals(task.status)) {
                    if (buildFinished && now - task.completedAt > FOLDER_DOWNLOAD_TASK_TTL_MS) {
                        task.status = "expired";
                        task.message = "下载文件已过期";
                        shouldRemove = true;
                    } else {
                        addProtectedPath(protectedPaths, taskPath);
                    }
                } else if (("failed".equals(task.status) || "canceled".equals(task.status)) && buildFinished) {
                    shouldRemove = true;
                } else if ("canceled".equals(task.status)
                        && now - task.lastActivityAt > FOLDER_DOWNLOAD_TASK_TTL_MS) {
                    shouldCancel = true;
                } else {
                    addProtectedPath(protectedPaths, taskPath);
                }
            }

            if (shouldCancel) {
                log.warn("文件夹打包任务超过2小时无活动，自动取消: taskId={}", task.taskId);
                boolean canceled = cancelFolderDownloadTaskInternal(
                        task, "文件夹打包超时，已自动取消", false);
                if (!canceled) {
                    synchronized (task) {
                        addProtectedPath(protectedPaths, normalizePath(task.zipPath));
                    }
                }
            } else if (shouldRemove) {
                deleteFolderDownloadZip(task.zipPath);
                folderDownloadTasks.remove(entry.getKey(), task);
            }
        }

        if (scanOrphanFiles) {
            cleanupOrphanFolderDownloadFiles(protectedPaths, now);
        }
    }

    private FileDownloadVO downloadDirectory(FileInfo dirInfo) {
        Path zipPath = null;
        Path activeZipPath = null;
        String streamId = null;
        try {
            zipPath = createFolderDownloadZipPath(FOLDER_DOWNLOAD_DIRECT_PREFIX);
            activeZipPath = normalizePath(zipPath);
            activeFolderDownloadPaths.add(activeZipPath);
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipPath))) {
                writeDirectoryToZip(dirInfo, sanitizeZipName(dirInfo.getDisplayName()), zipOutputStream, new HashSet<>());
            }

            long zipSize = Files.size(zipPath);
            streamId = IdUtil.fastSimpleUUID();
            String downloadStreamId = streamId;
            Path downloadZipPath = activeZipPath;
            DeleteOnCloseInputStream managedInputStream = new DeleteOnCloseInputStream(
                    Files.newInputStream(zipPath),
                    zipPath,
                    true,
                    () -> {
                        activeFolderDownloadStreams.remove(downloadStreamId);
                        activeFolderDownloadPaths.remove(downloadZipPath);
                    }
            );
            activeFolderDownloadStreams.put(downloadStreamId, managedInputStream);
            InputStream inputStream = managedInputStream;
            FileDownloadVO downloadVO = new FileDownloadVO();
            downloadVO.setFileName(buildZipFileName(dirInfo.getDisplayName()));
            downloadVO.setFileSize(zipSize);
            downloadVO.setResource(new InputStreamResource(inputStream));
            return downloadVO;
        } catch (Exception e) {
            if (streamId != null) {
                activeFolderDownloadStreams.remove(streamId);
            }
            if (activeZipPath != null) {
                activeFolderDownloadPaths.remove(activeZipPath);
            }
            deleteFolderDownloadZip(zipPath);
            throw new StorageOperationException("文件夹下载失败: " + e.getMessage(), e);
        }
    }

    private void writeDirectoryToZip(FileInfo dirInfo, String dirPath,
                                     ZipOutputStream zipOutputStream, Set<String> visitedDirIds) throws IOException {
        if (!visitedDirIds.add(dirInfo.getId())) {
            return;
        }

        String normalizedDirPath = ensureTrailingSlash(dirPath);
        zipOutputStream.putNextEntry(new ZipEntry(normalizedDirPath));
        zipOutputStream.closeEntry();

        for (FileInfo child : listDirectoryChildren(dirInfo)) {
            String childPath = normalizedDirPath + sanitizeZipName(child.getDisplayName());
            if (Boolean.TRUE.equals(child.getIsDir())) {
                writeDirectoryToZip(child, childPath, zipOutputStream, visitedDirIds);
            } else {
                writeFileToZip(child, childPath, zipOutputStream);
            }
        }
    }

    private void writeFileToZip(FileInfo fileInfo, String zipEntryName, ZipOutputStream zipOutputStream) throws IOException {
        IStorageOperationService storageService = storageServiceFacade.getStorageService(fileInfo.getStoragePlatformSettingId());
        if (!storageService.isFileExist(fileInfo.getObjectKey())) {
            log.warn("文件夹打包时跳过不存在的文件: fileId={}, objectKey={}", fileInfo.getId(), fileInfo.getObjectKey());
            return;
        }

        zipOutputStream.putNextEntry(new ZipEntry(zipEntryName));
        try (InputStream inputStream = storageService.downloadFile(fileInfo.getObjectKey())) {
            inputStream.transferTo(zipOutputStream);
        } finally {
            zipOutputStream.closeEntry();
        }
    }

    private List<FileInfo> listDirectoryChildren(FileInfo dirInfo) {
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.where(FILE_INFO.PARENT_ID.eq(dirInfo.getId())
                .and(FILE_INFO.WORKSPACE_ID.eq(dirInfo.getWorkspaceId()))
                .and(FILE_INFO.IS_DELETED.eq(false)));
        if (StringUtils.isEmpty(dirInfo.getStoragePlatformSettingId())) {
            queryWrapper.and(FILE_INFO.STORAGE_PLATFORM_SETTING_ID.isNull());
        } else {
            queryWrapper.and(FILE_INFO.STORAGE_PLATFORM_SETTING_ID.eq(dirInfo.getStoragePlatformSettingId()));
        }
        queryWrapper.orderBy(FILE_INFO.IS_DIR.desc())
                .orderBy(FILE_INFO.DISPLAY_NAME.asc());
        return fileInfoService.list(queryWrapper);
    }

    private String sanitizeZipName(String name) {
        String safeName = StringUtils.isEmpty(name) ? "未命名" : name;
        safeName = safeName.replace("\\", "/");
        safeName = safeName.replaceAll("^/+", "");
        safeName = safeName.replace("../", "");
        return safeName.replace("/", "_");
    }

    private String ensureTrailingSlash(String path) {
        return path.endsWith("/") ? path : path + "/";
    }

    private String buildZipFileName(String displayName) {
        String safeName = StringUtils.isEmpty(displayName) ? "文件夹" : displayName;
        return safeName.toLowerCase(Locale.ROOT).endsWith(".zip") ? safeName : safeName + ".zip";
    }

    private static class DeleteOnCloseInputStream extends FilterInputStream {
        private final Path path;
        private final boolean deletePathOnClose;
        private final Runnable onClose;
        private volatile long lastActivityAt = System.currentTimeMillis();
        private boolean closed;

        protected DeleteOnCloseInputStream(InputStream in, Path path, boolean deletePathOnClose,
                                          Runnable onClose) {
            super(in);
            this.path = path;
            this.deletePathOnClose = deletePathOnClose;
            this.onClose = onClose;
        }

        private Path getPath() {
            return path;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            lastActivityAt = System.currentTimeMillis();
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = super.read(buffer, offset, length);
            lastActivityAt = System.currentTimeMillis();
            return count;
        }

        private long getLastActivityAt() {
            return lastActivityAt;
        }

        @Override
        public synchronized void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            try {
                super.close();
            } finally {
                try {
                    if (deletePathOnClose) {
                        Files.deleteIfExists(path);
                    }
                } finally {
                    if (onClose != null) {
                        onClose.run();
                    }
                }
            }
        }
    }

    private static class FolderDownloadCanceledException extends IOException {
        private FolderDownloadCanceledException(String message) {
            super(message);
        }
    }

    private static class FolderDownloadTask {
        private String taskId;
        private String userId;
        private String workspaceId;
        private String storagePlatformSettingId;
        private String folderId;
        private String folderName;
        private volatile String status;
        private Integer progress;
        private Integer totalFiles;
        private Integer processedFiles;
        private Long totalBytes;
        private Long processedBytes;
        private Long zipSize;
        private String zipFileName;
        private volatile Path zipPath;
        private String message;
        private String errorMessage;
        private long createdAt;
        private long updatedAt;
        private long completedAt;
        private long lastActivityAt;
        private long downloadStartedAt;
        private int activeDownloadCount;
        private volatile boolean cancelRequested;
        private volatile boolean buildStarted;
        private volatile Future<?> buildFuture;
        private volatile InputStream currentInputStream;
        private final CountDownLatch buildFinished = new CountDownLatch(1);
    }

    private static class FolderDownloadScanResult {
        private int totalFiles;
        private long totalBytes;
    }

    /**
     * 初始化下载任务
     *
     * @param cmd 初始化下载命令
     * @return 初始化结果
     * @author xddcode
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InitDownloadResultVO initDownload(InitDownloadCmd cmd) {
        String userId = StpUtil.getLoginIdAsString();
        String workspaceId = WorkspaceContext.getWorkspaceId();
        String storagePlatformSettingId = StoragePlatformContextHolder.getConfigId();
        String taskId = null;

        try {
            SysUserTransferSetting userSetting = userTransferSettingService.getByUser();
            Integer maxConcurrentDownloads = userSetting != null && userSetting.getConcurrentDownloadQuantity() != null
                    ? userSetting.getConcurrentDownloadQuantity()
                    : 3;

            QueryWrapper queryWrapper = new QueryWrapper();
            queryWrapper.where(FILE_TRANSFER_TASK.USER_ID.eq(userId)
                    .and(FILE_TRANSFER_TASK.WORKSPACE_ID.eq(workspaceId))
                    .and(FILE_TRANSFER_TASK.TASK_TYPE.eq(TransferTaskType.download))
                    .and(FILE_TRANSFER_TASK.STORAGE_PLATFORM_SETTING_ID.eq(storagePlatformSettingId))
                    .and(FILE_TRANSFER_TASK.STATUS.in(
                            TransferTaskStatus.initialized,
                            TransferTaskStatus.downloading,
                            TransferTaskStatus.paused
                    )));
            long currentDownloadCount = this.count(queryWrapper);

            if (currentDownloadCount >= maxConcurrentDownloads) {
                throw new BusinessException(
                        String.format("已达到最大并发下载任务数限制（%d/%d），请等待其他任务完成后再试",
                                currentDownloadCount, maxConcurrentDownloads)
                );
            }

            FileInfo fileInfo = fileInfoService.getAuthorizedFile(cmd.getFileId());

            IStorageOperationService storageService =
                    storageServiceFacade.getStorageService(fileInfo.getStoragePlatformSettingId());
            if (!storageService.isFileExist(fileInfo.getObjectKey())) {
                throw new BusinessException(I18nUtils.getMessage("file.not.in.storage"));
            }

            Long chunkSize = cmd.getChunkSize();
            if (chunkSize == null || chunkSize <= 0) {
                chunkSize = userTransferSettingService.getChunkSize(userId);
            }

            int totalChunks = calculateTotalChunks(fileInfo.getSize(), chunkSize);

            taskId = IdUtil.fastSimpleUUID();
            FileTransferTask task = new FileTransferTask();
            task.setTaskId(taskId);
            task.setUserId(userId);
            task.setWorkspaceId(workspaceId);
            task.setParentId(fileInfo.getParentId());
            task.setFileName(fileInfo.getDisplayName());
            task.setFileSize(fileInfo.getSize());
            task.setSuffix(fileInfo.getSuffix());
            task.setMimeType(fileInfo.getMimeType());
            task.setTotalChunks(totalChunks);
            task.setUploadedChunks(0);
            task.setTaskType(TransferTaskType.download);
            task.setChunkSize(chunkSize);
            task.setObjectKey(fileInfo.getObjectKey());
            task.setStoragePlatformSettingId(fileInfo.getStoragePlatformSettingId());
            task.setStatus(TransferTaskStatus.initialized);
            task.setStartTime(LocalDateTime.now());

            this.save(task);
            cacheManager.cacheTask(task);
            cacheManager.recordStartTime(taskId);

            Set<Integer> downloadedChunks = getDownloadedChunks(taskId);

            transferSseService.sendStatusEvent(userId, taskId,
                    TransferTaskStatus.initialized.name(), "下载任务初始化成功");

            log.info("初始化下载任务成功: taskId={}, fileId={}, fileName={}, totalChunks={}, currentDownloads={}/{}",
                    taskId, cmd.getFileId(), fileInfo.getDisplayName(), totalChunks,
                    currentDownloadCount + 1, maxConcurrentDownloads);

            return InitDownloadResultVO.builder()
                    .taskId(taskId)
                    .fileName(fileInfo.getDisplayName())
                    .fileSize(fileInfo.getSize())
                    .totalChunks(totalChunks)
                    .chunkSize(chunkSize)
                    .downloadedChunks(downloadedChunks)
                    .build();

        } catch (BusinessException e) {
            log.error("初始化下载任务失败: fileId={}", cmd.getFileId(), e);

            // 统一处理业务异常
            if (taskId != null) {
                downloadExceptionHandler.handleDownloadTaskFailed(taskId, e.getMessage(), e);
            }
            throw e;
        } catch (Exception e) {
            log.error("初始化下载任务失败: fileId={}", cmd.getFileId(), e);

            if (taskId != null) {
                downloadExceptionHandler.handleDownloadTaskFailed(taskId,
                        I18nUtils.getMessage("transfer.download.init.failed", new Object[]{e.getMessage()}), e);
            }
            throw new StorageOperationException(I18nUtils.getMessage("transfer.download.init.failed", new Object[]{e.getMessage()}), e);
        }
    }

    /**
     * 下载分片
     *
     * @param taskId     任务ID
     * @param chunkIndex 分片索引
     * @return 分片数据流
     * @author xddcode
     */
    @Override
    public InputStream downloadChunk(String taskId, Integer chunkIndex) {
        FileTransferTask task = null;

        try {
            task = getTaskFromCacheOrDB(taskId);

            if (task.getTaskType() != TransferTaskType.download) {
                throw new BusinessException(I18nUtils.getMessage("task.type.incorrect", 
                    new Object[]{"download", task.getTaskType()}));
            }

            if (chunkIndex < 0 || chunkIndex >= task.getTotalChunks()) {
                throw new BusinessException(
                        String.format("分片索引无效: %d，有效范围: [0, %d)", chunkIndex, task.getTotalChunks())
                );
            }

            long startByte = (long) chunkIndex * task.getChunkSize();
            long endByte = Math.min(startByte + task.getChunkSize() - 1, task.getFileSize() - 1);

            log.info("下载分片: taskId={}, chunkIndex={}, range=[{}, {}]",
                    taskId, chunkIndex, startByte, endByte);

            IStorageOperationService storageService =
                    storageServiceFacade.getStorageService(task.getStoragePlatformSettingId());
            InputStream inputStream = storageService.downloadFileRange(
                    task.getObjectKey(), startByte, endByte);

            SysUserTransferSetting userSetting = userTransferSettingService.getByUser();
            if (userSetting != null && userSetting.getDownloadSpeedLimit() != null
                    && userSetting.getDownloadSpeedLimit() > 0) {
                long maxBytesPerSecond = (long) userSetting.getDownloadSpeedLimit() * 1024 * 1024;
                inputStream = new com.xddcodec.fs.file.utils.ThrottledInputStream(inputStream, maxBytesPerSecond);
                log.debug("应用下载速率限制: taskId={}, speedLimit={} MB/s",
                        taskId, userSetting.getDownloadSpeedLimit());
            }

            CompletableFuture.runAsync(() -> {
                try {
                    markChunkDownloaded(taskId, chunkIndex);
                } catch (Exception e) {
                    log.error("记录下载进度失败: taskId={}, chunkIndex={}", taskId, chunkIndex, e);
                }
            }, chunkUploadExecutor);

            return inputStream;

        } catch (BusinessException e) {
            log.error("下载分片失败: taskId={}, chunkIndex={}", taskId, chunkIndex, e);

            // 统一处理业务异常
            if (task != null) {
                downloadExceptionHandler.handleChunkDownloadFailed(taskId, chunkIndex, e.getMessage(), e);
            }
            throw e;
        } catch (StorageOperationException e) {
            log.error("存储读取失败: taskId={}, chunkIndex={}", taskId, chunkIndex, e);

            // 处理存储操作异常
            if (task != null) {
                downloadExceptionHandler.handleStorageReadFailed(taskId, task.getObjectKey(), e);
            }
            throw e;
        } catch (Exception e) {
            log.error("下载分片失败: taskId={}, chunkIndex={}", taskId, chunkIndex, e);

            // 统一处理其他异常
            if (task != null) {
                downloadExceptionHandler.handleChunkDownloadFailed(taskId, chunkIndex,
                        I18nUtils.getMessage("transfer.download.chunk.failed", new Object[]{e.getMessage()}), e);
            }
            throw new StorageOperationException(I18nUtils.getMessage("transfer.download.chunk.failed.detail"), e);
        }
    }

    /**
     * 记录分片下载完成
     *
     * @param taskId     任务ID
     * @param chunkIndex 分片索引
     * @author xddcode
     */
    @Override
    public void markChunkDownloaded(String taskId, Integer chunkIndex) {
        try {
            String chunksKey = "download:chunks:" + taskId;
            if (cacheManager.sHasKey(chunksKey, chunkIndex)) {
                log.debug("分片已记录，跳过: taskId={}, chunkIndex={}", taskId, chunkIndex);
                return;
            }

            FileTransferTask task = getTaskFromCacheOrDB(taskId);

            cacheManager.sSetAndTime(chunksKey, 7 * 24 * 60 * 60, chunkIndex);

            Long downloadedCount = cacheManager.sGetSetSize(chunksKey);
            task.setUploadedChunks(downloadedCount.intValue());
            this.updateById(task);

            long chunkBytes = task.getChunkSize();
            if (chunkIndex == task.getTotalChunks() - 1) {
                chunkBytes = task.getFileSize() - ((long) chunkIndex * task.getChunkSize());
            }
            cacheManager.recordTransferredBytes(taskId, chunkBytes);

            long downloadedBytes = cacheManager.getTransferredBytes(taskId);
            transferSseService.sendProgressEvent(task.getUserId(), taskId,
                    downloadedBytes, task.getFileSize(), downloadedCount.intValue(), task.getTotalChunks());

            log.info("记录下载进度: taskId={}, chunkIndex={}, progress={}/{}",
                    taskId, chunkIndex, downloadedCount, task.getTotalChunks());

            if (downloadedCount.intValue() >= task.getTotalChunks()) {
                updateTaskStatus(task, TransferTaskStatus.completed);
                task.setCompleteTime(LocalDateTime.now());
                this.updateById(task);

                transferSseService.sendCompleteEvent(task.getUserId(), taskId,
                        task.getFileName(), task.getFileName(), task.getFileSize());

                try {
                    cacheManager.deleteKey(chunksKey);
                    log.debug("清理下载分片记录: taskId={}", taskId);
                } catch (Exception cleanupEx) {
                    log.warn("清理下载分片记录失败: taskId={}", taskId, cleanupEx);
                }

                log.info("下载任务完成: taskId={}, fileName={}", taskId, task.getFileName());
            }

        } catch (Exception e) {
            log.error("记录下载进度失败: taskId={}, chunkIndex={}", taskId, chunkIndex, e);
            // 不抛出异常，避免影响下载流程
        }
    }

    /**
     * 获取已下载的分片列表
     *
     * @param taskId 任务ID
     * @return 已下载分片索引集合
     * @author xddcode
     */
    @Override
    public Set<Integer> getDownloadedChunks(String taskId) {
        try {
            getAuthorizedTask(taskId);
            String chunksKey = "download:chunks:" + taskId;
            Set<Object> chunks = cacheManager.sGet(chunksKey);

            if (chunks == null || chunks.isEmpty()) {
                return Collections.emptySet();
            }

            // 转换为 Integer Set
            Set<Integer> result = new HashSet<>();
            for (Object chunk : chunks) {
                if (chunk instanceof Integer) {
                    result.add((Integer) chunk);
                } else {
                    try {
                        result.add(Integer.parseInt(chunk.toString()));
                    } catch (NumberFormatException e) {
                        log.error("解析分片索引失败: chunk={}", chunk, e);
                    }
                }
            }

            log.debug("查询已下载分片: taskId={}, count={}", taskId, result.size());
            return result;

        } catch (Exception e) {
            log.error("查询已下载分片失败: taskId={}", taskId, e);
            return Collections.emptySet();
        }
    }

    @Override
    public FileTransferTask getTask(String taskId) {
        return getAuthorizedTask(taskId);
    }

}
