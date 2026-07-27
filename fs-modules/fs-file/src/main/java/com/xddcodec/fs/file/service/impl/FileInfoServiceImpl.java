package com.xddcodec.fs.file.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.util.UpdateEntity;
import com.xddcodec.fs.file.domain.FileInfo;
import com.xddcodec.fs.file.domain.dto.CopyFileCmd;
import com.xddcodec.fs.file.domain.dto.CreateDirectoryCmd;
import com.xddcodec.fs.file.domain.dto.MoveFileCmd;
import com.xddcodec.fs.file.domain.dto.RenameFileCmd;
import com.xddcodec.fs.file.domain.qry.FileQry;
import com.xddcodec.fs.file.domain.table.FileInfoTableDef;
import com.xddcodec.fs.file.domain.table.FileUserFavoritesTableDef;
import com.xddcodec.fs.file.domain.vo.FileDetailVO;
import com.xddcodec.fs.file.domain.vo.FileVO;
import com.xddcodec.fs.file.mapper.FileInfoMapper;
import com.xddcodec.fs.file.service.FileInfoService;
import com.xddcodec.fs.framework.common.domain.PageResult;
import com.xddcodec.fs.framework.common.enums.FileTypeEnum;
import com.xddcodec.fs.framework.common.context.WorkspaceContext;
import com.xddcodec.fs.framework.common.exception.BusinessException;
import com.xddcodec.fs.framework.common.exception.StorageOperationException;
import com.xddcodec.fs.framework.common.utils.I18nUtils;
import com.xddcodec.fs.framework.common.utils.StringUtils;
import com.xddcodec.fs.storage.plugin.core.IStorageOperationService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.xddcodec.fs.storage.plugin.core.context.StoragePlatformContextHolder;
import com.xddcodec.fs.storage.facade.StorageServiceFacade;
import io.github.linpeilie.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.xddcodec.fs.file.domain.table.FileInfoTableDef.FILE_INFO;
import static com.xddcodec.fs.file.domain.table.FileUserFavoritesTableDef.FILE_USER_FAVORITES;

/**
 * 文件资源服务实现类
 *
 * @Author: xddcode
 * @Date: 2025/5/8 9:40
 */
@Slf4j
@Service
public class FileInfoServiceImpl extends ServiceImpl<FileInfoMapper, FileInfo> implements FileInfoService {

    @Autowired
    private Converter converter;

    @Autowired
    private StorageServiceFacade storageServiceFacade;

    @Override
    public FileInfo getAuthorizedFile(String fileId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        if (StrUtil.isBlank(workspaceId) || StrUtil.isBlank(fileId)) {
            throw new BusinessException(I18nUtils.getMessage("file.not.found"));
        }
        FileInfo fileInfo = getOne(new QueryWrapper()
                .where(FILE_INFO.ID.eq(fileId))
                .and(FILE_INFO.WORKSPACE_ID.eq(workspaceId))
                .and(FILE_INFO.IS_DELETED.eq(false)));
        if (fileInfo == null) {
            throw new BusinessException(I18nUtils.getMessage("file.not.found"));
        }
        return fileInfo;
    }

    @Override
    public InputStream downloadFile(String fileId) {
        FileInfo fileInfo = getAuthorizedFile(fileId);
        if (fileInfo.getIsDir()) {
            throw new StorageOperationException(I18nUtils.getMessage("file.cannot.download.dir", new Object[]{fileId}));
        }
        if (fileInfo.getIsDeleted()) {
            throw new StorageOperationException(I18nUtils.getMessage("file.deleted", new Object[]{fileId}));
        }

        // 根据文件记录中的 storagePlatformSettingId 获取对应的存储服务
        try {
            IStorageOperationService storageService = storageServiceFacade.getStorageService(fileInfo.getStoragePlatformSettingId());
            return storageService.downloadFile(fileInfo.getObjectKey());
        } catch (StorageOperationException e) {
            // 直接抛出原始异常，让上层统一处理
            log.error("从存储平台下载文件失败: fileId={}, objectKey={}", fileId, fileInfo.getObjectKey(), e);
            throw e;
        }
    }

    @Override
    public String getFileUrl(String fileId, Integer expireSeconds) {
        FileInfo fileInfo = getAuthorizedFile(fileId);
        if (fileInfo.getIsDir()) {
            throw new StorageOperationException(I18nUtils.getMessage("file.dir.no.url", new Object[]{fileId}));
        }
        if (fileInfo.getIsDeleted()) {
            throw new StorageOperationException(I18nUtils.getMessage("file.deleted", new Object[]{fileId}));
        }
        IStorageOperationService storageService = storageServiceFacade.getStorageService(fileInfo.getStoragePlatformSettingId());
        if (!storageService.isFileExist(fileInfo.getObjectKey())) {
            throw new StorageOperationException(I18nUtils.getMessage("file.not.exist", new Object[]{fileId}));
        }
        return storageService.getFileUrl(fileInfo.getObjectKey(), expireSeconds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveFilesToRecycleBin(List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }

        String workspaceId = WorkspaceContext.getWorkspaceId();
        List<FileInfo> fileInfoList = list(new QueryWrapper()
                .where(FILE_INFO.ID.in(fileIds))
                .and(FILE_INFO.WORKSPACE_ID.eq(workspaceId)));
        if (fileInfoList.isEmpty()) {
            return;
        }

        // 收集所有需要删除的文件（包括子文件）
        List<FileInfo> toDeleteList = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (FileInfo fileInfo : fileInfoList) {
            if (!fileInfo.getIsDeleted()) {
                toDeleteList.add(fileInfo);

                // 如果是文件夹，递归获取所有子文件和子文件夹
                if (fileInfo.getIsDir()) {
                    List<FileInfo> children = getAllChildrenRecursively(fileInfo.getId(), workspaceId, false);
                    toDeleteList.addAll(children);
                }
            }
        }

        if (toDeleteList.isEmpty()) {
            return;
        }

        // 批量标记为删除
        toDeleteList.forEach(fileInfo -> {
            fileInfo.setIsDeleted(true);
            fileInfo.setDeletedTime(now);
        });

        this.updateBatch(toDeleteList);
    }


    /**
     * 递归获取文件夹下的所有子文件和子文件夹
     *
     * @param parentId       父文件夹ID
     * @param includeDeleted 是否包含已删除的文件
     * @return 所有子文件列表
     */
    private List<FileInfo> getAllChildrenRecursively(String parentId, String workspaceId, boolean includeDeleted) {
        List<FileInfo> allChildren = new ArrayList<>();

        // 查询直接子文件
        QueryWrapper query = new QueryWrapper()
                .where(FILE_INFO.PARENT_ID.eq(parentId))
                .and(FILE_INFO.WORKSPACE_ID.eq(workspaceId));

        if (!includeDeleted) {
            query.and(FILE_INFO.IS_DELETED.eq(false));
        }

        List<FileInfo> directChildren = list(query);

        for (FileInfo child : directChildren) {
            allChildren.add(child);

            // 如果是文件夹，递归查询
            if (child.getIsDir()) {
                allChildren.addAll(getAllChildrenRecursively(child.getId(), workspaceId, includeDeleted));
            }
        }

        return allChildren;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfo createDirectory(CreateDirectoryCmd cmd) {
        String folderId = IdUtil.fastSimpleUUID();
        String userId = StpUtil.getLoginIdAsString();
        String workspaceId = WorkspaceContext.getWorkspaceId();
        String platformConfigId = StoragePlatformContextHolder.getConfigId();
        if (StrUtil.isNotBlank(cmd.getParentId())) {
            FileInfo parent = getAuthorizedFile(cmd.getParentId());
            if (!Boolean.TRUE.equals(parent.getIsDir())) {
                throw new BusinessException(I18nUtils.getMessage("file.target.dir.invalid"));
            }
        }
        String baseName = cmd.getFolderName().trim();
        String finalName = generateUniqueName(
                workspaceId,
                cmd.getParentId(),
                baseName,
                true,
                null,
                platformConfigId
        );
        FileInfo dirInfo = new FileInfo();
        dirInfo.setId(folderId);
        dirInfo.setOriginalName(finalName);
        dirInfo.setDisplayName(finalName);
        dirInfo.setIsDir(true);
        dirInfo.setParentId(cmd.getParentId());
        dirInfo.setWorkspaceId(workspaceId);
        dirInfo.setUserId(userId);
        dirInfo.setStoragePlatformSettingId(platformConfigId);
        LocalDateTime now = LocalDateTime.now();
        dirInfo.setUploadTime(now);
        dirInfo.setUpdateTime(now);
        dirInfo.setIsDeleted(false);
        save(dirInfo);
        return dirInfo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renameFile(String fileId, RenameFileCmd cmd) {
        FileInfo fileInfo = getAuthorizedFile(fileId);
        if (fileInfo.getDisplayName().equals(cmd.getDisplayName())) {
            return;
        }
        String storagePlatformSettingId = StoragePlatformContextHolder.getConfigId();
        String newName = cmd.getDisplayName().trim();
        String finalName = generateUniqueName(
                fileInfo.getWorkspaceId(),
                fileInfo.getParentId(),
                newName,
                fileInfo.getIsDir(),
                fileId,
                storagePlatformSettingId
        );
        fileInfo.setDisplayName(finalName);
        LocalDateTime now = LocalDateTime.now();
        fileInfo.setUpdateTime(now);
        fileInfo.setLastAccessTime(now);
        updateById(fileInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveFile(MoveFileCmd cmd) {
        if (CollUtil.isEmpty(cmd.getFileIds())) {
            throw new BusinessException(I18nUtils.getMessage("file.id.list.empty"));
        }

        String targetDirId = StringUtils.isBlank(cmd.getDirId()) ? null : cmd.getDirId();
        String workspaceId = WorkspaceContext.getWorkspaceId();

        if (targetDirId != null) {
            FileInfo dirInfo = getAuthorizedFile(targetDirId);
            if (dirInfo == null || !dirInfo.getIsDir()) {
                throw new BusinessException(I18nUtils.getMessage("file.target.dir.invalid"));
            }
        }

        List<FileInfo> fileInfos = list(new QueryWrapper()
                .where(FILE_INFO.ID.in(cmd.getFileIds()))
                .and(FILE_INFO.WORKSPACE_ID.eq(workspaceId))
                .and(FILE_INFO.IS_DELETED.eq(false)));
        List<FileInfo> updateList = new ArrayList<>();

        for (FileInfo fileInfo : fileInfos) {
            if (Objects.equals(fileInfo.getParentId(), targetDirId)) {
                continue;
            }

            if (targetDirId != null && fileInfo.getIsDir()) {
                if (fileInfo.getId().equals(targetDirId) || isSubDirectory(fileInfo.getId(), targetDirId, workspaceId)) {
                    throw new BusinessException(I18nUtils.getMessage("file.cannot.move.to.self", 
                            new Object[]{fileInfo.getDisplayName()}));
                }
            }

            String finalName = generateUniqueName(
                    fileInfo.getWorkspaceId(),
                    targetDirId,
                    fileInfo.getDisplayName(),
                    fileInfo.getIsDir(),
                    fileInfo.getId(),
                    fileInfo.getStoragePlatformSettingId()
            );

            FileInfo updateEntity = UpdateEntity.of(FileInfo.class, fileInfo.getId());
            updateEntity.setParentId(targetDirId);
            updateEntity.setDisplayName(finalName);
            updateEntity.setUpdateTime(LocalDateTime.now());
            updateList.add(updateEntity);
        }

        if (!updateList.isEmpty()) {
            this.updateBatch(updateList);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<FileInfo> copyFiles(CopyFileCmd cmd) {
        if (CollUtil.isEmpty(cmd.getFileIds())) {
            throw new BusinessException(I18nUtils.getMessage("file.id.list.empty"));
        }

        String workspaceId = WorkspaceContext.getWorkspaceId();
        String userId = StpUtil.getLoginIdAsString();
        String targetDirId = StringUtils.isBlank(cmd.getDirId()) ? null : cmd.getDirId();
        String targetStorageId = StoragePlatformContextHolder.getConfigId();

        if (targetDirId != null) {
            FileInfo targetDir = getAuthorizedFile(targetDirId);
            if (!Boolean.TRUE.equals(targetDir.getIsDir())) {
                throw new BusinessException(I18nUtils.getMessage("file.target.dir.invalid"));
            }
            targetStorageId = targetDir.getStoragePlatformSettingId();
        }

        List<String> requestedIds = cmd.getFileIds().stream().distinct().toList();
        List<FileInfo> sourceFiles = list(new QueryWrapper()
                .where(FILE_INFO.ID.in(requestedIds))
                .and(FILE_INFO.WORKSPACE_ID.eq(workspaceId))
                .and(FILE_INFO.IS_DELETED.eq(false)));
        Map<String, FileInfo> sourceMap = sourceFiles.stream()
                .collect(Collectors.toMap(FileInfo::getId, file -> file));

        if (sourceMap.size() != requestedIds.size()) {
            throw new BusinessException(I18nUtils.getMessage("file.not.found"));
        }

        List<FileInfo> copiedTopLevelFiles = new ArrayList<>();
        for (String sourceId : requestedIds) {
            FileInfo source = sourceMap.get(sourceId);
            if (!sameStorage(source.getStoragePlatformSettingId(), targetStorageId)) {
                throw new BusinessException(I18nUtils.getMessage("file.cannot.copy.across.storage"));
            }
            if (targetDirId != null && Boolean.TRUE.equals(source.getIsDir())
                    && (source.getId().equals(targetDirId)
                    || isSubDirectory(source.getId(), targetDirId, workspaceId))) {
                throw new BusinessException(I18nUtils.getMessage(
                        "file.cannot.copy.to.self", new Object[]{source.getDisplayName()}));
            }

            String topLevelName = generateUniqueName(
                    workspaceId,
                    targetDirId,
                    source.getDisplayName(),
                    source.getIsDir(),
                    null,
                    targetStorageId
            );
            LocalDateTime now = LocalDateTime.now();
            List<FileInfo> copies = new ArrayList<>();
            FileInfo topLevelCopy = cloneFileInfo(source, targetDirId, topLevelName, userId, now);
            copies.add(topLevelCopy);
            if (Boolean.TRUE.equals(source.getIsDir())) {
                cloneDirectoryChildren(source.getId(), topLevelCopy.getId(), workspaceId, userId, now, copies);
            }

            // 每个顶层项目单独批量落库，让下一项的重名检测能够看到前一项。
            this.saveBatch(copies);
            copiedTopLevelFiles.add(topLevelCopy);
        }
        return copiedTopLevelFiles;
    }

    private void cloneDirectoryChildren(String sourceParentId,
                                        String targetParentId,
                                        String workspaceId,
                                        String userId,
                                        LocalDateTime now,
                                        List<FileInfo> copies) {
        List<FileInfo> children = list(new QueryWrapper()
                .where(FILE_INFO.PARENT_ID.eq(sourceParentId))
                .and(FILE_INFO.WORKSPACE_ID.eq(workspaceId))
                .and(FILE_INFO.IS_DELETED.eq(false))
                .orderBy(FILE_INFO.IS_DIR.desc(), FILE_INFO.UPLOAD_TIME.asc()));

        for (FileInfo child : children) {
            FileInfo childCopy = cloneFileInfo(
                    child,
                    targetParentId,
                    child.getDisplayName(),
                    userId,
                    now
            );
            copies.add(childCopy);
            if (Boolean.TRUE.equals(child.getIsDir())) {
                cloneDirectoryChildren(child.getId(), childCopy.getId(), workspaceId, userId, now, copies);
            }
        }
    }

    private FileInfo cloneFileInfo(FileInfo source,
                                   String parentId,
                                   String displayName,
                                   String userId,
                                   LocalDateTime now) {
        FileInfo copy = new FileInfo();
        copy.setId(IdUtil.fastSimpleUUID());
        copy.setObjectKey(source.getObjectKey());
        copy.setOriginalName(source.getOriginalName());
        copy.setDisplayName(displayName);
        copy.setSuffix(source.getSuffix());
        copy.setSize(source.getSize());
        copy.setMimeType(source.getMimeType());
        copy.setIsDir(source.getIsDir());
        copy.setParentId(parentId);
        copy.setWorkspaceId(source.getWorkspaceId());
        copy.setUserId(userId);
        copy.setContentMd5(source.getContentMd5());
        copy.setStoragePlatformSettingId(source.getStoragePlatformSettingId());
        copy.setUploadTime(now);
        copy.setUpdateTime(now);
        copy.setLastAccessTime(null);
        copy.setIsDeleted(false);
        copy.setDeletedTime(null);
        return copy;
    }

    private boolean sameStorage(String left, String right) {
        return Objects.equals(StrUtil.blankToDefault(left, ""), StrUtil.blankToDefault(right, ""));
    }

    // 检查target Id是否是source Id的子目录
    private boolean isSubDirectory(String sourceId, String targetId, String workspaceId) {
        FileInfo current = getOne(new QueryWrapper()
                .where(FILE_INFO.ID.eq(targetId))
                .and(FILE_INFO.WORKSPACE_ID.eq(workspaceId)));
        while (current != null && current.getParentId() != null) {
            if (current.getParentId().equals(sourceId)) {
                return true;
            }
            current = getOne(new QueryWrapper()
                    .where(FILE_INFO.ID.eq(current.getParentId()))
                    .and(FILE_INFO.WORKSPACE_ID.eq(workspaceId)));
        }
        return false;
    }

    /**
     * 生成唯一的文件名（处理重名冲突）
     * <p>
     * - 如果不存在重名：返回原名称
     * - 如果存在重名：自动添加 (1), (2), (3)... 后缀
     *
     * @param workspaceId   工作空间ID
     * @param parentId      父目录ID
     * @param desiredName   期望的文件名
     * @param isDir         是否是文件夹
     * @param excludeFileId 排除的文件ID（可选，用于重命名场景）
     * @return 唯一的文件名
     */
    @Override
    public String generateUniqueName(String workspaceId, String parentId,
                                     String desiredName, Boolean isDir,
                                     String excludeFileId, String storagePlatformSettingId) {

        String nameWithoutExt = desiredName;
        String extension = "";
        if (!isDir && desiredName.contains(".")) {
            int lastDotIndex = desiredName.lastIndexOf(".");
            nameWithoutExt = desiredName.substring(0, lastDotIndex);
            extension = desiredName.substring(lastDotIndex);
        }
        QueryWrapper query = buildSameLevelQuery(
                workspaceId,
                parentId,
                nameWithoutExt,
                isDir,
                excludeFileId,
                storagePlatformSettingId
        );
        List<FileInfo> existingFiles = list(query);
        if (existingFiles.isEmpty()) {
            return desiredName;
        }
        Set<Integer> usedSuffixes = extractUsedSuffixes(existingFiles, nameWithoutExt, isDir);
        int suffixNum = 0;
        String finalName;
        do {
            suffixNum++;
            finalName = buildNameWithSuffix(nameWithoutExt, suffixNum, extension, isDir);
        } while (usedSuffixes.contains(suffixNum));
        log.info("检测到重名，自动重命名：{} -> {}", desiredName, finalName);
        return finalName;
    }

    /**
     * 构建查询同级目录下同类型文件的条件
     */
    private QueryWrapper buildSameLevelQuery(String workspaceId, String parentId,
                                             String baseName, Boolean isDir,
                                             String excludeFileId, String storagePlatformSettingId) {
        QueryWrapper query = new QueryWrapper();

        query.where(FILE_INFO.WORKSPACE_ID.eq(workspaceId))
                .and(FILE_INFO.IS_DIR.eq(isDir))
                .and(FILE_INFO.IS_DELETED.eq(false));

        if (StrUtil.isBlank(parentId)) {
            query.and(FILE_INFO.PARENT_ID.isNull());
        } else {
            query.and(FILE_INFO.PARENT_ID.eq(parentId));
        }
        // ------------------------------------

        query.and(FILE_INFO.DISPLAY_NAME.like(baseName + "%"));

        if (StringUtils.isEmpty(storagePlatformSettingId)) {
            query.and(FILE_INFO.STORAGE_PLATFORM_SETTING_ID.isNull());
        } else {
            query.and(FILE_INFO.STORAGE_PLATFORM_SETTING_ID.eq(storagePlatformSettingId));
        }

        if (StrUtil.isNotBlank(excludeFileId)) {
            query.and(FILE_INFO.ID.ne(excludeFileId));
        }
        return query;
    }

    /**
     * 提取已使用的后缀数字
     * 示例：
     * - photo.jpg       -> 0
     * - photo(1).jpg    -> 1
     * - photo(2).jpg    -> 2
     * - photo(abc).jpg  -> -1 (忽略)
     */
    private Set<Integer> extractUsedSuffixes(List<FileInfo> existingFiles,
                                             String nameWithoutExt,
                                             Boolean isDir) {
        return existingFiles.stream()
                .map(f -> {
                    String displayName = f.getDisplayName();

                    // 移除扩展名（如果是文件）
                    if (!isDir && displayName.contains(".")) {
                        int lastDotIndex = displayName.lastIndexOf(".");
                        displayName = displayName.substring(0, lastDotIndex);
                    }

                    // 检查是否完全匹配基础名称（表示原始文件，后缀为 0）
                    if (displayName.equals(nameWithoutExt)) {
                        return 0;
                    }

                    // 匹配 (n) 格式的后缀
                    String pattern = "^" + Pattern.quote(nameWithoutExt) + "\\((\\d+)\\)$";
                    Matcher matcher = Pattern.compile(pattern).matcher(displayName);

                    if (matcher.find()) {
                        return Integer.parseInt(matcher.group(1));
                    }

                    return -1; // 不匹配的名称（忽略）
                })
                .filter(n -> n >= 0)  // 只保留有效的后缀数字
                .collect(Collectors.toSet());
    }

    /**
     * 构建带后缀的文件名
     *
     * @param nameWithoutExt 不含扩展名的文件名
     * @param suffixNum      后缀数字
     * @param extension      扩展名（含点号）
     * @param isDir          是否是文件夹
     * @return 完整的文件名
     */
    private String buildNameWithSuffix(String nameWithoutExt, int suffixNum,
                                       String extension, Boolean isDir) {
        if (isDir) {
            // 文件夹：baseName(1)
            return nameWithoutExt + "(" + suffixNum + ")";
        } else {
            // 文件：baseName(1).ext
            return nameWithoutExt + "(" + suffixNum + ")" + extension;
        }
    }

    @Override
    public List<FileVO> getDirectoryTreePath(String dirId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        FileInfo fileInfo = getAuthorizedFile(dirId);

        List<FileVO> pathList = new ArrayList<>();
        FileInfo current = fileInfo;

        // 递归向上查找，直到根节点（parent_id 为 null）
        while (current != null) {
            FileVO fileVO = converter.convert(current, FileVO.class);
            pathList.add(0, fileVO);

            // 查找父节点
            if (current.getParentId() != null) {
                current = getOne(new QueryWrapper()
                        .where(FILE_INFO.ID.eq(current.getParentId()))
                        .and(FILE_INFO.WORKSPACE_ID.eq(workspaceId))
                        .and(FILE_INFO.IS_DELETED.eq(false)));
            } else {
                break;
            }
        }

        return pathList;
    }

    @Override
    public PageResult<FileVO> getList(FileQry qry) {
        String userId = StpUtil.getLoginIdAsString();
        String workspaceId = WorkspaceContext.getWorkspaceId();
        String storagePlatformSettingId = StoragePlatformContextHolder.getConfigId();

        int pageNum = qry.getPage() == null ? 1 : qry.getPage();
        int pageSize = qry.getPageSize() == null ? 10 : qry.getPageSize();
        Page<FileVO> pageParam = new Page<>(pageNum, pageSize);

        QueryWrapper wrapper = new QueryWrapper();
        wrapper.select(
                        "fi.*",
                        "CASE WHEN fuf.file_id IS NOT NULL THEN 1 ELSE 0 END AS is_favorite"
                )
                .from(FILE_INFO.as("fi"))
                .leftJoin(FILE_USER_FAVORITES.as("fuf"))
                .on(FILE_INFO.ID.eq(FILE_USER_FAVORITES.FILE_ID)
                        .and(FILE_USER_FAVORITES.USER_ID.eq(userId)))
                .where(FILE_INFO.WORKSPACE_ID.eq(workspaceId))
                .and(FILE_INFO.IS_DELETED.eq(false));
        // 存储平台过滤
        if (StringUtils.isEmpty(storagePlatformSettingId)) {
            wrapper.and(FILE_INFO.STORAGE_PLATFORM_SETTING_ID.isNull());
        } else {
            wrapper.and(FILE_INFO.STORAGE_PLATFORM_SETTING_ID.eq(storagePlatformSettingId));
        }
        // 最近使用视图 (Recents)
        if (Boolean.TRUE.equals(qry.getIsRecents())) {
            wrapper.and(FILE_INFO.IS_DIR.eq(false))
                    .orderBy(FILE_INFO.LAST_ACCESS_TIME.desc());
            // 最近使用通常不需要分页，只需要前 N 条，或者也可以直接分页查询第一页
            pageParam.setPageSize(20);
        } else {
            // 收藏过滤
            if (Boolean.TRUE.equals(qry.getIsFavorite()) && qry.getParentId() == null) {
                wrapper.and(FILE_USER_FAVORITES.FILE_ID.isNotNull());
            }

            // 目录/文件视图过滤
            if (Boolean.TRUE.equals(qry.getIsDir())) {
                wrapper.and(FILE_INFO.IS_DIR.eq(true));
            }

            // 父目录逻辑：判断是否是特殊筛选视图
            boolean isTypeFilter = StrUtil.isNotBlank(qry.getFileType());
            boolean isFavoriteView = Boolean.TRUE.equals(qry.getIsFavorite()) && qry.getParentId() == null;
            boolean isDirFilter = Boolean.TRUE.equals(qry.getIsDir()) && qry.getParentId() == null;
            boolean isWorkspaceSearch = StrUtil.isNotBlank(qry.getKeyword()) && qry.getParentId() == null;

            if (!isTypeFilter && !isFavoriteView && !isDirFilter && !isWorkspaceSearch) {
                if (qry.getParentId() == null) {
                    wrapper.and(FILE_INFO.PARENT_ID.isNull());
                } else {
                    wrapper.and(FILE_INFO.PARENT_ID.eq(qry.getParentId()));
                }
            }

            // 关键词搜索
            if (StrUtil.isNotBlank(qry.getKeyword())) {
                String kw = qry.getKeyword().trim();
                wrapper.and(FILE_INFO.ORIGINAL_NAME.like(kw).or(FILE_INFO.DISPLAY_NAME.like(kw)));
            }

            // 文件类型过滤 (内部调用 applyFileTypeFilter)
            applyFileTypeFilter(wrapper, qry);

            // 排序逻辑
            String orderByField = switch (String.valueOf(qry.getOrderBy())) {
                case "displayName" -> "display_name";
                case "suffix" -> "suffix";
                case "size" -> "size";
                default -> "update_time";
            };
            boolean isAsc = "ASC".equalsIgnoreCase(qry.getOrderDirection());

            wrapper.orderBy(FILE_INFO.IS_DIR.desc());
            if ("display_name".equals(orderByField)) {
                // 逐段按数值比较名称中的数字，避免 1、10、2，以及 2 (1)、2 (10)、2 (2)。
                wrapper.orderBy("CASE WHEN display_name REGEXP '[0-9]' THEN 0 ELSE 1 END", isAsc)
                        .orderBy("LOWER(REGEXP_SUBSTR(display_name, '^[^0-9]*'))", isAsc)
                        .orderBy("CAST(REGEXP_SUBSTR(display_name, '[0-9]+', 1, 1) AS UNSIGNED)", isAsc)
                        .orderBy("CAST(REGEXP_SUBSTR(display_name, '[0-9]+', 1, 2) AS UNSIGNED)", isAsc)
                        .orderBy("CAST(REGEXP_SUBSTR(display_name, '[0-9]+', 1, 3) AS UNSIGNED)", isAsc)
                        .orderBy("LOWER(display_name)", isAsc);
            } else {
                wrapper.orderBy(orderByField, isAsc);
            }
            // 保证分页排序稳定，避免排序字段相同时跨页重复或遗漏。
            wrapper.orderBy(FILE_INFO.ID.asc());
        }

        // 执行分页查询 (使用 listAs 直接映射到 VO)
        Page<FileVO> resultPage = this.pageAs(pageParam, wrapper, FileVO.class);

        // 异步补充缩略图 (这里用 parallelStream 没问题，或者在转换后处理)
        if (CollUtil.isNotEmpty(resultPage.getRecords())) {
            resultPage.getRecords().parallelStream().forEach(vo ->
                    vo.setThumbnailUrl(fillThumbnailUrl(vo.getId(), vo.getSuffix()))
            );
        }

        return PageResult.success(resultPage.getRecords(), resultPage.getTotalRow());
    }

    /**
     * 填充图片封面链接
     */
    private String fillThumbnailUrl(String fileId, String suffix) {
        // 如果非图片文件跳过
        if (!FileTypeEnum.isImageFile(suffix)) {
            return null;
        }
        // 统一走同源文件流，避免把 minio/rustfs 等 Docker 内部地址返回给浏览器。
        return "/api/file/stream/preview/" + fileId;
    }

    @Override
    public Long calculateUsedStorage() {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        String storagePlatformSettingId = StoragePlatformContextHolder.getConfigId();

        List<FileInfo> fileInfoList = this.list(new QueryWrapper()
                .where(FILE_INFO.WORKSPACE_ID.eq(workspaceId)
                        .and(FILE_INFO.STORAGE_PLATFORM_SETTING_ID.eq(storagePlatformSettingId))
                        .and(FILE_INFO.IS_DELETED.eq(false))
                        .and(FILE_INFO.IS_DIR.eq(false))
                ));

        // 统计总大小
        return fileInfoList.stream()
                .map(FileInfo::getSize)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
    }

    @Override
    public FileDetailVO getFileDetails(String fileId) {
        FileInfo fileInfo = getAuthorizedFile(fileId);
        FileDetailVO vo = converter.convert(fileInfo, FileDetailVO.class);
        if (vo.getIsDir()) {
            Map<String, Long> stats = new HashMap<>();
            stats.put("size", 0L);
            stats.getOrDefault("fileCount", 0L);
            stats.put("fileCount", 0L);
            stats.put("folderCount", 0L);
            recursiveAccumulate(fileId, stats);

            //如果为文件夹则需要统计该文件夹下所有文件
            vo.setSize(stats.get("size"));
            vo.setIncludeFiles(stats.get("fileCount").intValue());
            vo.setIncludeFolders(stats.get("folderCount").intValue());
        } else {
            vo.setIncludeFiles(0);
            vo.setIncludeFolders(0);
            vo.setThumbnailUrl(fillThumbnailUrl(fileInfo.getId(), vo.getSuffix()));
        }
        return vo;
    }

    /**
     * 递归统计文件夹信息
     */
    private void recursiveAccumulate(String parentId, Map<String, Long> stats) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        String storagePlatformSettingId = StoragePlatformContextHolder.getConfigId();
        List<FileInfo> children = this.list(new QueryWrapper()
                .where(FILE_INFO.PARENT_ID.eq(parentId))
                .and(FILE_INFO.WORKSPACE_ID.eq(workspaceId))
                .and(FILE_INFO.STORAGE_PLATFORM_SETTING_ID.eq(storagePlatformSettingId))
                .and(FILE_INFO.IS_DELETED.eq(false)));

        if (CollUtil.isEmpty(children)) {
            return;
        }

        for (FileInfo child : children) {
            if (child.getIsDir()) {
                // 统计文件夹个数并向下递归
                stats.put("folderCount", stats.get("folderCount") + 1);
                recursiveAccumulate(child.getId(), stats);
            } else {
                // 统计文件个数及大小
                stats.put("fileCount", stats.get("fileCount") + 1);
                long fileSize = child.getSize() != null ? child.getSize() : 0L;
                stats.put("size", stats.get("size") + fileSize);
            }
        }
    }

    @Override
    public List<FileVO> getDirs(String parentId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        String storagePlatformSettingId = StoragePlatformContextHolder.getConfigId();
        QueryWrapper wrapper = new QueryWrapper();
        wrapper.where(FILE_INFO.WORKSPACE_ID.eq(workspaceId)
                .and(FILE_INFO.STORAGE_PLATFORM_SETTING_ID.eq(storagePlatformSettingId))
                .and(FILE_INFO.IS_DELETED.eq(false))
                .and(FILE_INFO.IS_DIR.eq(true))
        );

        if (StrUtil.isNotBlank(parentId)) {
            wrapper.and(FILE_INFO.PARENT_ID.eq(parentId));
        } else {
            wrapper.and(FILE_INFO.PARENT_ID.isNull());
        }
        wrapper.orderBy(FILE_INFO.UPDATE_TIME.desc());
        return this.listAs(wrapper, FileVO.class);
    }

    @Override
    public List<FileVO> getByFileIds(List<String> fileIds) {
        if (CollUtil.isEmpty(fileIds)) {
            return List.of();
        }
        List<FileInfo> fileInfos = this.list(new QueryWrapper()
                .where(FILE_INFO.ID.in(fileIds))
                .and(FILE_INFO.IS_DELETED.eq(false)));
        return converter.convert(fileInfos, FileVO.class);
    }

    /**
     * 应用文件类型过滤
     */
    private void applyFileTypeFilter(QueryWrapper wrapper, FileQry qry) {
        if (qry.getFileType() == null || qry.getFileType().trim().isEmpty()) {
            return;
        }
        FileTypeEnum.FileCategory category = FileTypeEnum.FileCategory.fromCode(qry.getFileType());
        if (category != null) {
            // 按大类筛选
            applyFilterByCategory(wrapper, category);
            return;
        }
        FileTypeEnum fileType = FileTypeEnum.fromType(qry.getFileType());
        if (fileType == null) {
            log.warn("未识别的文件类型: {}", qry.getFileType());
            return;
        }
        // 按具体类型筛选
        applyFilterByType(wrapper, fileType);
    }

    /**
     * 按分类筛选
     */
    private void applyFilterByCategory(QueryWrapper wrapper, FileTypeEnum.FileCategory category) {
        List<String> categorySuffixes = FileTypeEnum.getSuffixesByCategory(category);

        if (category == FileTypeEnum.FileCategory.OTHER) {
            // OTHER 分类：匹配 OTHER 分类的已知后缀 + 所有未知后缀
            List<String> allOtherKnownSuffixes = FileTypeEnum.getAllKnownSuffixesExcluding(FileTypeEnum.FileCategory.OTHER);

            wrapper.and(FILE_INFO.IS_DIR.eq(false))
                    .and(
                            FILE_INFO.SUFFIX.in(categorySuffixes) // zip、rar 等
                                    .or(FILE_INFO.SUFFIX.notIn(allOtherKnownSuffixes)) // 真正的未知类型
                                    .or(FILE_INFO.SUFFIX.isNull().or(FILE_INFO.SUFFIX.eq("")))
                    );
        } else {
            // 常规分类：直接匹配后缀
            if (!categorySuffixes.isEmpty()) {
                wrapper.and(FILE_INFO.IS_DIR.eq(false))
                        .and(FILE_INFO.SUFFIX.in(categorySuffixes));
            }
        }
    }

    /**
     * 按具体类型筛选
     */
    private void applyFilterByType(QueryWrapper wrapper, FileTypeEnum fileType) {
        if (fileType.isOther()) {
            // 其他类型：排除所有已知后缀
            List<String> knownSuffixes = FileTypeEnum.getAllKnownSuffixes();
            wrapper.and(FILE_INFO.IS_DIR.eq(false))
                    .and(
                            FILE_INFO.SUFFIX.notIn(knownSuffixes)
                                    .or(FILE_INFO.SUFFIX.isNull().or(FILE_INFO.SUFFIX.eq("")))
                    );
        } else {
            // 具体类型：直接匹配后缀
            List<String> suffixes = fileType.getSuffixes();
            if (suffixes != null && !suffixes.isEmpty()) {
                wrapper.and(FILE_INFO.IS_DIR.eq(false))
                        .and(FILE_INFO.SUFFIX.in(suffixes));
            }
        }
    }
}
