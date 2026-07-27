package com.xddcodec.fs.file.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import com.xddcodec.fs.file.domain.FileInfo;
import com.xddcodec.fs.file.domain.FileUserFavorites;
import com.xddcodec.fs.file.mapper.FileUserFavoritesMapper;
import com.xddcodec.fs.file.service.FileUserFavoritesService;
import com.xddcodec.fs.framework.common.context.WorkspaceContext;
import com.xddcodec.fs.framework.common.exception.BusinessException;
import com.xddcodec.fs.framework.common.utils.I18nUtils;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.xddcodec.fs.storage.plugin.core.context.StoragePlatformContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.xddcodec.fs.file.domain.table.FileInfoTableDef.FILE_INFO;
import static com.xddcodec.fs.file.domain.table.FileUserFavoritesTableDef.FILE_USER_FAVORITES;

/**
 * 用户收藏文件服务实现类
 *
 * @Author: xddcode
 * @Date: 2025/5/12 13:50
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUserFavoritesServiceImpl extends ServiceImpl<FileUserFavoritesMapper, FileUserFavorites> implements FileUserFavoritesService {

    private final FileInfoServiceImpl fileInfoService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void favoritesFile(List<String> fileIds) {
        if (CollUtil.isEmpty(fileIds)) {
            throw new BusinessException(I18nUtils.getMessage("file.favorites.empty"));
        }
        List<String> distinctFileIds = fileIds.stream().distinct().collect(Collectors.toList());
        String userId = StpUtil.getLoginIdAsString();
        String workspaceId = WorkspaceContext.getWorkspaceId();
        List<FileInfo> fileInfos = fileInfoService.list(
                QueryWrapper.create()
                        .where(FILE_INFO.ID.in(distinctFileIds))
                        .and(FILE_INFO.WORKSPACE_ID.eq(workspaceId))
                        .and(FILE_INFO.IS_DELETED.eq(false))
        );
        if (fileInfos.isEmpty()) {
            throw new BusinessException(I18nUtils.getMessage("file.favorites.not.found"));
        }
        // 查询已收藏的文件
        List<FileUserFavorites> existingFavorites = list(
                new QueryWrapper()
                        .where(FILE_USER_FAVORITES.FILE_ID.in(fileIds))
                        .and(FILE_USER_FAVORITES.USER_ID.eq(userId))
        );
        Set<String> existingFileIds = existingFavorites.stream()
                .map(FileUserFavorites::getFileId)
                .collect(Collectors.toSet());
        // 过滤掉已收藏的文件
        List<FileUserFavorites> favoritesToAdd = fileInfos.stream()
                .filter(fileInfo -> !existingFileIds.contains(fileInfo.getId()))
                .map(fileInfo -> {
                    FileUserFavorites favoritesFile = new FileUserFavorites();
                    favoritesFile.setWorkspaceId(workspaceId);
                    favoritesFile.setFileId(fileInfo.getId());
                    favoritesFile.setUserId(userId);
                    return favoritesFile;
                })
                .collect(Collectors.toList());
        if (favoritesToAdd.isEmpty()) {
            log.info("用户 {} 的文件已全部收藏，fileIds: {}", userId, fileIds);
            return;
        }
        this.saveBatch(favoritesToAdd);

        // 修改访问时间
        LocalDateTime now = LocalDateTime.now();
        List<String> fileIdsToUpdate = favoritesToAdd.stream()
                .map(FileUserFavorites::getFileId)
                .collect(Collectors.toList());

        fileIdsToUpdate.forEach(fileId -> {
            FileInfo fileInfo = new FileInfo();
            fileInfo.setId(fileId);
            fileInfo.setLastAccessTime(now);
            fileInfoService.updateById(fileInfo);
        });

        log.info("用户 {} 成功收藏 {} 个文件，并更新了访问时间", userId, favoritesToAdd.size());
    }

    @Override
    public void unFavoritesFile(List<String> fileIds) {
        if (CollUtil.isEmpty(fileIds)) {
            return;
        }

        String userId = StpUtil.getLoginIdAsString();
        List<String> distinctFileIds = fileIds.stream().distinct().collect(Collectors.toList());

        // 删除收藏记录
        this.remove(
                new QueryWrapper()
                        .where(FILE_USER_FAVORITES.FILE_ID.in(distinctFileIds))
                        .and(FILE_USER_FAVORITES.USER_ID.eq(userId))
        );

        // 修改访问时间
        LocalDateTime now = LocalDateTime.now();
        distinctFileIds.forEach(fileId -> {
            FileInfo fileInfo = new FileInfo();
            fileInfo.setId(fileId);
            fileInfo.setLastAccessTime(now);
            fileInfoService.updateById(fileInfo);
        });

        log.info("用户 {} 成功取消收藏 {} 个文件，并更新了访问时间", userId, distinctFileIds.size());
    }

    @Override
    public void removeByFileIds(Collection<String> fileIds, String userId) {
        if (CollUtil.isEmpty(fileIds)) {
            return;
        }
        this.remove(new QueryWrapper()
                .where(FILE_USER_FAVORITES.FILE_ID.in(fileIds))
                .and(FILE_USER_FAVORITES.USER_ID.eq(userId)));
    }

    @Override
    public void removeByFileIds(Collection<String> fileIds) {
        if (CollUtil.isEmpty(fileIds)) {
            return;
        }
        this.remove(new QueryWrapper()
                .where(FILE_USER_FAVORITES.FILE_ID.in(fileIds)));
    }

    @Override
    public Long getFavoritesCount() {
        String userId = StpUtil.getLoginIdAsString();
        String workspaceId = WorkspaceContext.getWorkspaceId();
        String storagePlatformSettingId = StoragePlatformContextHolder.getConfigId();
        return this.count(new QueryWrapper()
                .from(FILE_USER_FAVORITES)
                .leftJoin(FILE_INFO).on(FILE_USER_FAVORITES.FILE_ID.eq(FILE_INFO.ID))
                .where(FILE_USER_FAVORITES.USER_ID.eq(userId))
                .and(FILE_USER_FAVORITES.WORKSPACE_ID.eq(workspaceId))
                .and(FILE_INFO.STORAGE_PLATFORM_SETTING_ID.eq(storagePlatformSettingId))
                .and(FILE_INFO.IS_DELETED.eq(false))
        );
    }
}
