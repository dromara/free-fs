package com.xddcodec.fs.file.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import com.xddcodec.fs.file.domain.FileInfo;
import com.xddcodec.fs.file.domain.qry.FileRecycleQry;
import com.xddcodec.fs.file.domain.table.FileInfoTableDef;
import com.xddcodec.fs.file.domain.vo.FileRecycleVO;
import com.xddcodec.fs.file.service.FileInfoService;
import com.xddcodec.fs.file.service.FileObjectReferenceService;
import com.xddcodec.fs.file.service.FileRecycleService;
import com.xddcodec.fs.file.service.FileUserFavoritesService;
import com.xddcodec.fs.framework.common.context.WorkspaceContext;
import com.xddcodec.fs.framework.common.domain.PageResult;
import com.xddcodec.fs.framework.common.exception.BusinessException;
import com.xddcodec.fs.framework.common.utils.I18nUtils;
import com.xddcodec.fs.storage.plugin.core.context.StoragePlatformContextHolder;
import io.github.linpeilie.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.mybatisflex.core.query.QueryMethods.notExists;
import static com.xddcodec.fs.file.domain.table.FileInfoTableDef.FILE_INFO;

/**
 * 回收站服务接口实现
 *
 * @Author: xddcode
 * @Date: 2025/5/8 9:35
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileRecycleServiceImpl implements FileRecycleService {

    private final Converter converter;

    private final FileInfoService fileInfoService;

    private final FileUserFavoritesService fileUserFavoritesService;

    private final FileObjectReferenceService objectReferenceService;

    @Override
    public PageResult<FileRecycleVO> getRecyclePages(FileRecycleQry qry) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        String configId = StoragePlatformContextHolder.getConfigId();
        int pageNum = qry.getPage() == null ? 1 : qry.getPage();
        int pageSize = qry.getPageSize() == null ? 10 : qry.getPageSize();

        FileInfoTableDef t1 = FILE_INFO.as("t1");
        FileInfoTableDef t2 = FILE_INFO.as("t2");

        QueryWrapper queryWrapper = QueryWrapper.create()
                .select(t1.ALL_COLUMNS)
                .from(t1)
                .where(t1.WORKSPACE_ID.eq(workspaceId))
                .and(t1.STORAGE_PLATFORM_SETTING_ID.eq(configId))
                .and(t1.IS_DELETED.eq(true));

        if (StrUtil.isNotBlank(qry.getKeyword())) {
            String keyword = qry.getKeyword().trim();
            queryWrapper.and(
                    t1.ORIGINAL_NAME.like(keyword)
                            .or(t1.DISPLAY_NAME.like(keyword))
            );
        } else {
            queryWrapper.and(
                    t1.PARENT_ID.isNull()
                            .or(
                                    notExists(
                                            QueryWrapper.create()
                                                    .select(t2.ID)
                                                    .from(t2)
                                                    .where(t2.ID.eq(t1.PARENT_ID))
                                                    .and(t2.IS_DELETED.eq(true))
                                    )
                            )
            );
        }

        queryWrapper.orderBy(FILE_INFO.IS_DIR.desc())
                .orderBy(FILE_INFO.UPDATE_TIME.desc());

        Page<FileInfo> resultPage = fileInfoService.page(new Page<>(pageNum, pageSize), queryWrapper);

        List<FileRecycleVO> voList = converter.convert(resultPage.getRecords(), FileRecycleVO.class);
        return PageResult.success(voList, resultPage.getTotalRow());
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreFiles(List<String> fileIds) {
        if (CollUtil.isEmpty(fileIds)) return;
        String workspaceId = WorkspaceContext.getWorkspaceId();

        Set<String> allIdsToRestore = collectFileIdsRecursively(
                fileIds,
                workspaceId,
                wrapper -> wrapper.and(FILE_INFO.IS_DELETED.eq(true))
        );

        Set<String> parentIdsInRecycle = collectParentIdsInRecycle(fileIds, workspaceId);
        allIdsToRestore.addAll(parentIdsInRecycle);

        if (CollUtil.isEmpty(allIdsToRestore)) {
            throw new BusinessException(I18nUtils.getMessage("recycle.file.not.found"));
        }

        UpdateChain.of(FileInfo.class)
                .set(FileInfo::getIsDeleted, false)
                .set(FileInfo::getDeletedTime, null)
                .where(FILE_INFO.ID.in(allIdsToRestore))
                .and(FILE_INFO.WORKSPACE_ID.eq(workspaceId))
                .update();

        log.info("工作空间 {} 恢复文件/文件夹，共 {} 项", workspaceId, allIdsToRestore.size());
    }

    /**
     * 向上递归收集：找出这些文件在回收站中的所有祖先文件夹
     */
    private Set<String> collectParentIdsInRecycle(List<String> currentIds, String workspaceId) {
        Set<String> allParentIds = new HashSet<>();
        List<String> runnerIds = new ArrayList<>(currentIds);

        while (CollUtil.isNotEmpty(runnerIds)) {
            List<String> pIds = fileInfoService.queryChain()
                    .select(FILE_INFO.PARENT_ID)
                    .where(FILE_INFO.ID.in(runnerIds))
                    .and(FILE_INFO.PARENT_ID.isNotNull())
                    .and(FILE_INFO.WORKSPACE_ID.eq(workspaceId))
                    .listAs(String.class)
                    .stream().filter(StrUtil::isNotBlank).distinct().collect(Collectors.toList());

            if (CollUtil.isEmpty(pIds)) break;

            // 2. 看看这些 parentId 中，哪些还在回收站里 (is_deleted = true)
            List<String> deletedParents = fileInfoService.queryChain()
                    .select(FILE_INFO.ID)
                    .where(FILE_INFO.ID.in(pIds))
                    .and(FILE_INFO.IS_DELETED.eq(true))
                    .listAs(String.class);

            if (CollUtil.isEmpty(deletedParents)) break;

            // 3. 收集并继续往上找
            allParentIds.addAll(deletedParents);
            runnerIds = deletedParents;
        }
        return allParentIds;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void permanentlyDeleteFiles(List<String> fileIds) {
        if (CollUtil.isEmpty(fileIds)) {
            return;
        }
        String workspaceId = WorkspaceContext.getWorkspaceId();
        Set<String> allFileIds = collectFileIdsRecursively(
                fileIds,
                workspaceId,
                wrapper -> wrapper.and(FILE_INFO.IS_DELETED.eq(true))
        );
        if (CollUtil.isEmpty(allFileIds)) {
            throw new BusinessException(I18nUtils.getMessage("recycle.file.not.found.delete"));
        }
        List<FileInfo> allFiles = fileInfoService.listByIds(allFileIds);
        List<FileInfo> physicalObjects = allFiles.stream()
                .filter(file -> StrUtil.isNotBlank(file.getObjectKey()))
                .collect(Collectors.toMap(
                        file -> String.valueOf(file.getStoragePlatformSettingId()) + "|" + file.getObjectKey(),
                        file -> file,
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values().stream().toList();

        fileInfoService.removeByIds(allFileIds);

        try {
            String userId = StpUtil.getLoginIdAsString();
            fileUserFavoritesService.removeByFileIds(allFileIds, userId);
        } catch (Exception e) {
            fileUserFavoritesService.removeByFileIds(allFileIds);
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (FileInfo file : physicalObjects) {
                            try {
                                objectReferenceService.deletePhysicalFileIfUnreferencedWithLock(file);
                            } catch (Exception e) {
                                log.error("删除无引用物理文件失败: {}", file.getObjectKey(), e);
                            }
                        }
                    }
                }
        );
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void clearRecycles() {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        String configId = StoragePlatformContextHolder.getConfigId();

        FileInfoTableDef t1 = FILE_INFO.as("t1");
        FileInfoTableDef t2 = FILE_INFO.as("t2");

        // 直接从数据库查出所有回收站的“顶层项”ID
        List<String> topLevelIds = fileInfoService.queryChain()
                .select(t1.ID)
                .from(t1)
                .where(t1.WORKSPACE_ID.eq(workspaceId))
                .and(t1.STORAGE_PLATFORM_SETTING_ID.eq(configId))
                .and(t1.IS_DELETED.eq(true))
                .and(t1.PARENT_ID.isNull().or(
                        notExists(QueryWrapper.create().from(t2).where(t2.ID.eq(t1.PARENT_ID)).and(t2.IS_DELETED.eq(true)))
                ))
                .listAs(String.class);

        if (CollUtil.isNotEmpty(topLevelIds)) {
            this.permanentlyDeleteFiles(topLevelIds);
        }
    }


    /**
     * 递归收集文件ID（通用方法）
     *
     * @param fileIds     初始文件ID列表
     * @param workspaceId 工作空间ID
     * @param filter      过滤条件（可选）
     * @return 收集到的所有文件ID集合
     */
    private Set<String> collectFileIdsRecursively(
            List<String> fileIds,
            String workspaceId,
            Consumer<QueryWrapper> filter) {

        QueryWrapper initialWrapper = new QueryWrapper()
                .where(FILE_INFO.ID.in(fileIds))
                .and(FILE_INFO.WORKSPACE_ID.eq(workspaceId));

        if (filter != null) {
            filter.accept(initialWrapper);
        }

        List<FileInfo> files = fileInfoService.list(initialWrapper);

        if (CollUtil.isEmpty(files)) {
            return Collections.emptySet();
        }

        Set<String> allFileIds = new HashSet<>();
        files.forEach(file -> collectFileIdsRecursive(file, allFileIds, workspaceId, filter));

        return allFileIds;
    }

    private void collectFileIdsRecursive(
            FileInfo file,
            Set<String> allFileIds,
            String workspaceId,
            Consumer<QueryWrapper> filter) {

        allFileIds.add(file.getId());

        if (file.getIsDir()) {
            QueryWrapper wrapper = new QueryWrapper()
                    .where(FILE_INFO.PARENT_ID.eq(file.getId()))
                    .and(FILE_INFO.WORKSPACE_ID.eq(workspaceId));

            if (filter != null) {
                filter.accept(wrapper);
            }

            List<FileInfo> children = fileInfoService.list(wrapper);
            children.forEach(child -> collectFileIdsRecursive(child, allFileIds, workspaceId, filter));
        }
    }
}
