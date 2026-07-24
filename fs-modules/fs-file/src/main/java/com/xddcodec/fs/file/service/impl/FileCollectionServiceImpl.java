package com.xddcodec.fs.file.service.impl;

import cn.dev33.satoken.secure.SaSecureUtil;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.xddcodec.fs.file.cache.TransferTaskCacheManager;
import com.xddcodec.fs.file.domain.FileCollection;
import com.xddcodec.fs.file.domain.FileCollectionSubmission;
import com.xddcodec.fs.file.domain.FileInfo;
import com.xddcodec.fs.file.domain.FileTransferTask;
import com.xddcodec.fs.file.domain.dto.CreateFileCollectionCmd;
import com.xddcodec.fs.file.domain.dto.CreateFileCollectionSubmissionCmd;
import com.xddcodec.fs.file.domain.qry.FileCollectionQry;
import com.xddcodec.fs.file.domain.qry.FileCollectionSubmissionQry;
import com.xddcodec.fs.file.domain.vo.FileCollectionDeletionResult;
import com.xddcodec.fs.file.domain.vo.FileCollectionPublicVO;
import com.xddcodec.fs.file.domain.vo.FileCollectionSubmissionSessionVO;
import com.xddcodec.fs.file.domain.vo.FileCollectionSubmissionVO;
import com.xddcodec.fs.file.domain.vo.FileCollectionUploadContext;
import com.xddcodec.fs.file.domain.vo.FileCollectionVO;
import com.xddcodec.fs.file.enums.FileCollectionStatus;
import com.xddcodec.fs.file.enums.FileCollectionSubmissionStatus;
import com.xddcodec.fs.file.enums.TransferTaskStatus;
import com.xddcodec.fs.file.mapper.FileCollectionMapper;
import com.xddcodec.fs.file.mapper.FileCollectionSubmissionMapper;
import com.xddcodec.fs.file.mapper.FileTransferTaskMapper;
import com.xddcodec.fs.file.service.FileCollectionService;
import com.xddcodec.fs.file.service.FileInfoService;
import com.xddcodec.fs.framework.common.context.WorkspaceContext;
import com.xddcodec.fs.framework.common.domain.PageResult;
import com.xddcodec.fs.framework.common.exception.BusinessException;
import com.xddcodec.fs.framework.common.utils.IpUtils;
import com.xddcodec.fs.framework.redis.repository.RedisRepository;
import com.xddcodec.fs.system.auth.PasswordHashService;
import com.xddcodec.fs.system.domain.SysWorkspaceMember;
import com.xddcodec.fs.system.service.SysRolePermissionService;
import com.xddcodec.fs.system.service.SysWorkspaceMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.xddcodec.fs.file.domain.table.FileCollectionSubmissionTableDef.FILE_COLLECTION_SUBMISSION;
import static com.xddcodec.fs.file.domain.table.FileCollectionTableDef.FILE_COLLECTION;
import static com.xddcodec.fs.file.domain.table.FileTransferTaskTableDef.FILE_TRANSFER_TASK;

@Service
@RequiredArgsConstructor
public class FileCollectionServiceImpl
        extends ServiceImpl<FileCollectionMapper, FileCollection>
        implements FileCollectionService {

    private static final long DEFAULT_MAX_FILE_SIZE = 1024L * 1024 * 1024;
    private static final int MAX_SUBMISSION_SESSIONS_PER_HOUR = 20;
    private static final int MAX_CODE_ATTEMPTS_PER_TEN_MINUTES = 10;
    private static final DateTimeFormatter SUBMISSION_FOLDER_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String ACCESS_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final FileInfoService fileInfoService;
    private final FileCollectionSubmissionMapper submissionMapper;
    private final FileTransferTaskMapper transferTaskMapper;
    private final TransferTaskCacheManager transferTaskCacheManager;
    private final PasswordHashService passwordHashService;
    private final RedisRepository redisRepository;
    private final SysWorkspaceMemberService workspaceMemberService;
    private final SysRolePermissionService rolePermissionService;

    @Override
    public PageResult<FileCollectionVO> getPages(FileCollectionQry qry) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        int pageNumber = qry.getPage() == null ? 1 : qry.getPage();
        int pageSize = qry.getPageSize() == null ? 10 : Math.min(qry.getPageSize(), 100);
        Page<FileCollection> page = new Page<>(pageNumber, pageSize);

        QueryWrapper wrapper = new QueryWrapper()
                .where(FILE_COLLECTION.WORKSPACE_ID.eq(workspaceId));
        if (StrUtil.isNotBlank(qry.getKeyword())) {
            String keyword = "%" + qry.getKeyword().trim() + "%";
            wrapper.and(FILE_COLLECTION.COLLECTION_NAME.like(keyword)
                    .or(FILE_COLLECTION.DESCRIPTION.like(keyword)));
        }
        if (StrUtil.isNotBlank(qry.getStatus())) {
            try {
                wrapper.and(FILE_COLLECTION.STATUS.eq(
                        FileCollectionStatus.valueOf(qry.getStatus().trim().toUpperCase(Locale.ROOT))));
            } catch (IllegalArgumentException ignored) {
                throw new BusinessException(400, "无效的收集状态");
            }
        }
        applyOrder(wrapper, qry.getOrderBy(), qry.getOrderDirection());
        this.page(page, wrapper);
        List<FileCollectionVO> records = page.getRecords().stream()
                .map(this::buildVO)
                .toList();
        return PageResult.success(records, page.getTotalRow());
    }

    @Override
    public FileCollectionVO getDetail(String collectionId) {
        return buildVO(getOwnedCollection(collectionId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileCollectionVO createCollection(CreateFileCollectionCmd cmd) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        String userId = StpUtil.getLoginIdAsString();
        FileInfo targetFolder = fileInfoService.getAuthorizedFile(cmd.getTargetFolderId());
        if (!Boolean.TRUE.equals(targetFolder.getIsDir()) || Boolean.TRUE.equals(targetFolder.getIsDeleted())) {
            throw new BusinessException(400, "目标文件夹不存在或不可用");
        }

        FileCollection collection = new FileCollection();
        collection.setUserId(userId);
        collection.setWorkspaceId(workspaceId);
        collection.setTargetFolderId(targetFolder.getId());
        collection.setStoragePlatformSettingId(targetFolder.getStoragePlatformSettingId());
        collection.setCollectionName(cmd.getCollectionName().trim());
        collection.setDescription(StrUtil.trimToNull(cmd.getDescription()));
        collection.setExpireTime(calculateExpireTime(cmd.getExpireType(), cmd.getExpireTime()));
        collection.setMaxFileSize(cmd.getMaxFileSize() == null
                ? DEFAULT_MAX_FILE_SIZE : cmd.getMaxFileSize());
        collection.setAllowedExtensions(normalizeAllowedExtensions(cmd.getAllowedExtensions()));
        collection.setStatus(FileCollectionStatus.OPEN);
        collection.setSubmissionCount(0);
        collection.setFileCount(0);
        collection.setTotalSize(0L);

        String plainAccessCode = null;
        if (Boolean.TRUE.equals(cmd.getNeedAccessCode())) {
            plainAccessCode = StrUtil.isBlank(cmd.getAccessCode())
                    ? RandomUtil.randomString(ACCESS_CODE_ALPHABET, 6)
                    : cmd.getAccessCode().trim();
            if (plainAccessCode.length() < 4) {
                throw new BusinessException(400, "访问码至少需要4个字符");
            }
            collection.setAccessCodeHash(passwordHashService.encode(plainAccessCode));
        }

        this.save(collection);
        FileCollectionVO vo = buildVO(collection);
        vo.setAccessCode(plainAccessCode);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileCollectionVO updateStatus(String collectionId, FileCollectionStatus status) {
        FileCollection collection = getOwnedCollection(collectionId);
        if (FileCollectionStatus.OPEN.equals(status)) {
            if (!StpUtil.hasPermission("file:write")) {
                throw new BusinessException(403, "没有上传权限，无法重新开启文件收集");
            }
            ensureCollectionOwnerCanCollect(collection);
        }
        collection.setStatus(status);
        collection.setUpdatedAt(LocalDateTime.now());
        this.updateById(collection);
        return buildVO(collection);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileCollectionDeletionResult deleteCollection(String collectionId) {
        FileCollection collection = getOwnedCollection(collectionId);
        FileCollectionVO collectionVO = buildVO(collection);

        QueryWrapper submissionQuery = new QueryWrapper()
                .where(FILE_COLLECTION_SUBMISSION.COLLECTION_ID.eq(collectionId));
        int submissionCount = (int) submissionMapper.selectCountByQuery(submissionQuery);

        // 只清理已经结束的传输任务元数据。进行中的任务交给定时清理，避免删除时
        // 与分片上传/合并线程竞争；无论哪种状态，都不会触碰 FileInfo 或对象存储。
        QueryWrapper terminalTaskQuery = new QueryWrapper()
                .where(FILE_TRANSFER_TASK.COLLECTION_ID.eq(collectionId))
                .and(FILE_TRANSFER_TASK.WORKSPACE_ID.eq(collection.getWorkspaceId()))
                .and(FILE_TRANSFER_TASK.STATUS.in(
                        TransferTaskStatus.completed,
                        TransferTaskStatus.failed,
                        TransferTaskStatus.canceled));
        List<FileTransferTask> terminalTasks = transferTaskMapper.selectListByQuery(terminalTaskQuery);
        if (!terminalTasks.isEmpty()) {
            transferTaskMapper.deleteByQuery(terminalTaskQuery);
            transferTaskCacheManager.cleanTasks(terminalTasks.stream()
                    .map(FileTransferTask::getTaskId)
                    .filter(Objects::nonNull)
                    .toList());
        }

        submissionMapper.deleteByQuery(submissionQuery);
        this.removeById(collection.getId());

        return new FileCollectionDeletionResult(
                collectionVO,
                submissionCount,
                terminalTasks.size());
    }

    @Override
    public PageResult<FileCollectionSubmissionVO> getSubmissions(
            String collectionId, FileCollectionSubmissionQry qry) {
        getOwnedCollection(collectionId);
        int pageNumber = qry.getPage() == null ? 1 : qry.getPage();
        int pageSize = qry.getPageSize() == null ? 20 : Math.min(qry.getPageSize(), 100);
        Page<FileCollectionSubmission> page = new Page<>(pageNumber, pageSize);
        QueryWrapper wrapper = new QueryWrapper()
                .where(FILE_COLLECTION_SUBMISSION.COLLECTION_ID.eq(collectionId));
        if (StrUtil.isNotBlank(qry.getKeyword())) {
            String keyword = "%" + qry.getKeyword().trim() + "%";
            wrapper.and(FILE_COLLECTION_SUBMISSION.SUBMITTER_NAME.like(keyword));
        }
        wrapper.orderBy(FILE_COLLECTION_SUBMISSION.CREATED_AT.desc());
        submissionMapper.paginate(page, wrapper);
        return PageResult.success(page.getRecords().stream()
                .map(this::buildSubmissionVO)
                .toList(), page.getTotalRow());
    }

    @Override
    public FileCollectionPublicVO getPublicInfo(String collectionId) {
        FileCollection collection = getCollection(collectionId);
        ensureCollectionOwnerCanCollect(collection);
        FileCollectionPublicVO vo = new FileCollectionPublicVO();
        vo.setId(collection.getId());
        vo.setCollectionName(collection.getCollectionName());
        vo.setDescription(collection.getDescription());
        vo.setExpireTime(collection.getExpireTime());
        vo.setExpired(isExpired(collection));
        vo.setHasAccessCode(StrUtil.isNotBlank(collection.getAccessCodeHash()));
        vo.setMaxFileSize(collection.getMaxFileSize());
        vo.setAllowedExtensions(collection.getAllowedExtensions());
        vo.setStatus(collection.getStatus());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileCollectionSubmissionSessionVO startSubmission(
            String collectionId, CreateFileCollectionSubmissionCmd cmd) {
        FileCollection collection = getActiveCollection(collectionId);
        ensureCollectionOwnerCanCollect(collection);
        String ip = IpUtils.getIpAddr();
        checkSubmissionRateLimit(collectionId, ip);
        verifyAccessCode(collection, cmd.getAccessCode(), ip);

        FileInfo targetFolder = fileInfoService.getById(collection.getTargetFolderId());
        if (targetFolder == null
                || !Objects.equals(targetFolder.getWorkspaceId(), collection.getWorkspaceId())
                || !Boolean.TRUE.equals(targetFolder.getIsDir())
                || Boolean.TRUE.equals(targetFolder.getIsDeleted())) {
            throw new BusinessException(400, "收集目标文件夹已不存在");
        }

        String submitterName = sanitizeSubmitterName(cmd.getSubmitterName());
        String desiredFolderName = submitterName + "-" + LocalDateTime.now().format(SUBMISSION_FOLDER_TIME);
        String folderName = fileInfoService.generateUniqueName(
                collection.getWorkspaceId(), collection.getTargetFolderId(), desiredFolderName,
                true, null, collection.getStoragePlatformSettingId());

        FileInfo submissionFolder = new FileInfo();
        submissionFolder.setId(RandomUtil.randomString(32));
        submissionFolder.setOriginalName(folderName);
        submissionFolder.setDisplayName(folderName);
        submissionFolder.setIsDir(true);
        submissionFolder.setParentId(collection.getTargetFolderId());
        submissionFolder.setWorkspaceId(collection.getWorkspaceId());
        submissionFolder.setUserId(collection.getUserId());
        submissionFolder.setStoragePlatformSettingId(collection.getStoragePlatformSettingId());
        LocalDateTime now = LocalDateTime.now();
        submissionFolder.setUploadTime(now);
        submissionFolder.setUpdateTime(now);
        submissionFolder.setIsDeleted(false);
        fileInfoService.save(submissionFolder);

        String uploadToken = RandomUtil.randomString(64);
        FileCollectionSubmission submission = new FileCollectionSubmission();
        submission.setCollectionId(collectionId);
        submission.setSubmitterName(submitterName);
        submission.setSubmitterIp(ip);
        submission.setUserAgent(trim(IpUtils.getUserAgent(), 512));
        submission.setFolderId(submissionFolder.getId());
        submission.setUploadTokenHash(SaSecureUtil.sha256(uploadToken));
        submission.setFileCount(0);
        submission.setTotalSize(0L);
        submission.setStatus(FileCollectionSubmissionStatus.UPLOADING);
        submissionMapper.insert(submission);
        this.mapper.incrementSubmissionCount(collectionId, now);

        return FileCollectionSubmissionSessionVO.builder()
                .submissionId(submission.getId())
                .uploadToken(uploadToken)
                .folderName(folderName)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeSubmission(String collectionId, String submissionId, String uploadToken) {
        FileCollectionUploadContext context = authorizeToken(
                collectionId, submissionId, uploadToken, false, true);
        FileCollectionSubmission submission = context.getSubmission();
        if (FileCollectionSubmissionStatus.COMPLETED.equals(submission.getStatus())) {
            return;
        }
        if (submission.getFileCount() == null || submission.getFileCount() <= 0) {
            throw new BusinessException(400, "请至少成功上传一个文件后再完成提交");
        }
        submission.setStatus(FileCollectionSubmissionStatus.COMPLETED);
        submission.setCompletedAt(LocalDateTime.now());
        submission.setUpdatedAt(LocalDateTime.now());
        submissionMapper.update(submission);
    }

    @Override
    public FileCollectionUploadContext authorizeUpload(
            String collectionId, String submissionId, String uploadToken) {
        return authorizeToken(collectionId, submissionId, uploadToken, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordUploadedFile(String collectionId, String submissionId, long fileSize) {
        LocalDateTime now = LocalDateTime.now();
        int submissionRows = submissionMapper.incrementFileStats(submissionId, fileSize, now);
        int collectionRows = this.mapper.incrementFileStats(collectionId, fileSize, now);
        if (submissionRows != 1 || collectionRows != 1) {
            throw new BusinessException("更新文件收集统计失败");
        }
    }

    private FileCollectionUploadContext authorizeToken(
            String collectionId, String submissionId, String uploadToken,
            boolean requireActiveCollection) {
        return authorizeToken(collectionId, submissionId, uploadToken,
                requireActiveCollection, false);
    }

    private FileCollectionUploadContext authorizeToken(
            String collectionId, String submissionId, String uploadToken,
            boolean requireActiveCollection, boolean allowCompletedSubmission) {
        if (StrUtil.isBlank(uploadToken)) {
            throw new BusinessException(401, "缺少上传凭证");
        }
        FileCollection collection = requireActiveCollection
                ? getActiveCollection(collectionId) : getCollection(collectionId);
        FileCollectionSubmission submission = submissionMapper.selectOneByQuery(
                new QueryWrapper()
                        .where(FILE_COLLECTION_SUBMISSION.ID.eq(submissionId))
                        .and(FILE_COLLECTION_SUBMISSION.COLLECTION_ID.eq(collectionId)));
        if (submission == null) {
            throw new BusinessException(404, "提交记录不存在");
        }
        String providedHash = SaSecureUtil.sha256(uploadToken);
        if (!constantTimeEquals(providedHash, submission.getUploadTokenHash())) {
            throw new BusinessException(401, "上传凭证无效");
        }
        if (!allowCompletedSubmission
                && FileCollectionSubmissionStatus.COMPLETED.equals(submission.getStatus())) {
            throw new BusinessException(400, "本次提交已经完成");
        }
        return new FileCollectionUploadContext(collection, submission);
    }

    private FileCollection getOwnedCollection(String collectionId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        FileCollection collection = this.getOne(new QueryWrapper()
                .where(FILE_COLLECTION.ID.eq(collectionId))
                .and(FILE_COLLECTION.WORKSPACE_ID.eq(workspaceId)));
        if (collection == null) {
            throw new BusinessException(404, "文件收集不存在");
        }
        return collection;
    }

    private FileCollection getCollection(String collectionId) {
        FileCollection collection = this.getById(collectionId);
        if (collection == null) {
            throw new BusinessException(404, "文件收集不存在");
        }
        return collection;
    }

    private FileCollection getActiveCollection(String collectionId) {
        FileCollection collection = getCollection(collectionId);
        if (!FileCollectionStatus.OPEN.equals(collection.getStatus())) {
            throw new BusinessException(400, "文件收集已关闭");
        }
        if (isExpired(collection)) {
            throw new BusinessException(400, "文件收集已过期");
        }
        return collection;
    }

    private void ensureCollectionOwnerCanCollect(FileCollection collection) {
        SysWorkspaceMember owner = workspaceMemberService.findByWorkspaceAndUser(
                collection.getWorkspaceId(), collection.getUserId());
        if (owner == null) {
            throw new BusinessException(403, "文件收集创建者已不在工作空间，收集已停用");
        }
        List<String> permissions = rolePermissionService
                .getPermissionCodesByRoleId(owner.getRoleId());
        if (permissions == null
                || !permissions.contains("file:share")
                || !permissions.contains("file:write")) {
            throw new BusinessException(403, "文件收集创建者已无分享或上传权限，收集已停用");
        }
    }

    private void verifyAccessCode(FileCollection collection, String accessCode, String ip) {
        if (StrUtil.isBlank(collection.getAccessCodeHash())) {
            return;
        }
        String key = "file-collection:code-attempt:" + collection.getId() + ":" + ip;
        Long attempts = redisRepository.incr(key, 1);
        if (attempts != null && attempts == 1) {
            redisRepository.expire(key, 10 * 60);
        }
        if (attempts != null && attempts > MAX_CODE_ATTEMPTS_PER_TEN_MINUTES) {
            throw new BusinessException(429, "访问码尝试次数过多，请稍后再试");
        }
        if (StrUtil.isBlank(accessCode)
                || !passwordHashService.matches(accessCode.trim(), collection.getAccessCodeHash())) {
            throw new BusinessException(400, "访问码不正确");
        }
        redisRepository.del(key);
    }

    private void checkSubmissionRateLimit(String collectionId, String ip) {
        String key = "file-collection:submission-rate:" + collectionId + ":" + ip;
        Long count = redisRepository.incr(key, 1);
        if (count != null && count == 1) {
            redisRepository.expire(key, 60 * 60);
        }
        if (count != null && count > MAX_SUBMISSION_SESSIONS_PER_HOUR) {
            throw new BusinessException(429, "创建提交次数过多，请稍后再试");
        }
    }

    private FileCollectionVO buildVO(FileCollection collection) {
        FileCollectionVO vo = new FileCollectionVO();
        vo.setId(collection.getId());
        vo.setCollectionName(collection.getCollectionName());
        vo.setDescription(collection.getDescription());
        vo.setTargetFolderId(collection.getTargetFolderId());
        FileInfo targetFolder = fileInfoService.getById(collection.getTargetFolderId());
        vo.setTargetFolderName(targetFolder == null ? "目标文件夹已删除" : targetFolder.getDisplayName());
        vo.setExpireTime(collection.getExpireTime());
        vo.setPermanent(collection.getExpireTime() == null);
        vo.setExpired(isExpired(collection));
        vo.setHasAccessCode(StrUtil.isNotBlank(collection.getAccessCodeHash()));
        vo.setMaxFileSize(collection.getMaxFileSize());
        vo.setAllowedExtensions(collection.getAllowedExtensions());
        vo.setStatus(collection.getStatus());
        vo.setSubmissionCount(collection.getSubmissionCount());
        vo.setFileCount(collection.getFileCount());
        vo.setTotalSize(collection.getTotalSize());
        vo.setCreatedAt(collection.getCreatedAt());
        vo.setUpdatedAt(collection.getUpdatedAt());
        return vo;
    }

    private FileCollectionSubmissionVO buildSubmissionVO(FileCollectionSubmission submission) {
        FileCollectionSubmissionVO vo = new FileCollectionSubmissionVO();
        vo.setId(submission.getId());
        vo.setSubmitterName(submission.getSubmitterName());
        vo.setSubmitterIp(submission.getSubmitterIp());
        vo.setFolderId(submission.getFolderId());
        vo.setFileCount(submission.getFileCount());
        vo.setTotalSize(submission.getTotalSize());
        vo.setStatus(submission.getStatus());
        vo.setCreatedAt(submission.getCreatedAt());
        vo.setCompletedAt(submission.getCompletedAt());
        return vo;
    }

    private void applyOrder(QueryWrapper wrapper, String orderBy, String direction) {
        boolean asc = "ASC".equalsIgnoreCase(direction);
        String field = StrUtil.blankToDefault(orderBy, "createdAt");
        switch (field) {
            case "collectionName" -> wrapper.orderBy(FILE_COLLECTION.COLLECTION_NAME, asc);
            case "expireTime" -> wrapper.orderBy(FILE_COLLECTION.EXPIRE_TIME, asc);
            case "submissionCount" -> wrapper.orderBy(FILE_COLLECTION.SUBMISSION_COUNT, asc);
            case "fileCount" -> wrapper.orderBy(FILE_COLLECTION.FILE_COUNT, asc);
            case "totalSize" -> wrapper.orderBy(FILE_COLLECTION.TOTAL_SIZE, asc);
            default -> wrapper.orderBy(FILE_COLLECTION.CREATED_AT, asc);
        }
        wrapper.orderBy(FILE_COLLECTION.ID.desc());
    }

    private LocalDateTime calculateExpireTime(Integer expireType, LocalDateTime customExpireTime) {
        int type = expireType == null ? 1 : expireType;
        LocalDateTime now = LocalDateTime.now();
        return switch (type) {
            case 1 -> now.plusDays(7);
            case 2 -> now.plusDays(30);
            case 3 -> {
                if (customExpireTime == null || !customExpireTime.isAfter(now)) {
                    throw new BusinessException(400, "自定义截止时间必须晚于当前时间");
                }
                yield customExpireTime;
            }
            case 4 -> null;
            default -> throw new BusinessException(400, "无效的有效期类型");
        };
    }

    private String normalizeAllowedExtensions(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        List<String> extensions = Arrays.stream(raw.split("[,;\\s]+"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .map(value -> value.startsWith(".") ? value.substring(1) : value)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (extensions.size() > 50) {
            throw new BusinessException(400, "允许的文件类型不能超过50种");
        }
        for (String extension : extensions) {
            if (!extension.matches("[a-z0-9]{1,20}")) {
                throw new BusinessException(400, "文件类型格式不正确: " + extension);
            }
        }
        return extensions.isEmpty() ? null : extensions.stream().collect(Collectors.joining(","));
    }

    private String sanitizeSubmitterName(String value) {
        String sanitized = value == null ? "" : value
                .replaceAll("[\\p{Cntrl}/\\\\]", "_")
                .replaceAll("\\s+", " ")
                .trim();
        sanitized = sanitized.replaceAll("^[. ]+|[. ]+$", "");
        if (sanitized.isBlank()) {
            throw new BusinessException(400, "提交人姓名不能为空");
        }
        return sanitized.length() <= 64 ? sanitized : sanitized.substring(0, 64);
    }

    private boolean isExpired(FileCollection collection) {
        return collection.getExpireTime() != null
                && LocalDateTime.now().isAfter(collection.getExpireTime());
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
