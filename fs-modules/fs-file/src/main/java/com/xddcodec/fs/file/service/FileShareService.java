package com.xddcodec.fs.file.service;

import com.mybatisflex.core.service.IService;
import com.xddcodec.fs.file.domain.FileShare;
import com.xddcodec.fs.file.domain.dto.CreateShareCmd;
import com.xddcodec.fs.file.domain.dto.VerifyShareCodeCmd;
import com.xddcodec.fs.file.domain.qry.FileShareQry;
import com.xddcodec.fs.file.domain.vo.FileDownloadVO;
import com.xddcodec.fs.file.domain.vo.FileShareThinVO;
import com.xddcodec.fs.file.domain.vo.FileShareVO;
import com.xddcodec.fs.file.domain.vo.FileVO;
import com.xddcodec.fs.file.domain.vo.FolderDownloadTaskVO;
import com.xddcodec.fs.framework.common.domain.PageResult;

import java.util.List;

/**
 * 文件分享服务接口
 *
 * @Author: xddcode
 * @Date: 2025/10/30 9:35
 */
public interface FileShareService extends IService<FileShare> {

    /**
     * 分页查询我的分享
     *
     * @param qry
     * @return
     */
    PageResult<FileShareVO> getPages(FileShareQry qry);

    /**
     * 获取分享详情
     *
     * @param shareId 分享ID
     * @return
     */
    FileShareVO getDetail(String shareId);

    /**
     * 创建分享
     *
     * @param cmd 创建分享参数
     */
    FileShareVO createShare(CreateShareCmd cmd);

    /**
     * 取消分享
     *
     * @param ids 分享ID集合
     */
    void cancelShares(List<String> ids);

    /**
     * 取消所有分享
     */
    void cancelAllShares();

    /**
     * 校验提取码
     *
     * @param cmd
     */
    boolean verifyShareCode(VerifyShareCodeCmd cmd);

    /**
     * 获取文件分享页对象
     *
     * @param shareId 分享id
     * @return vo
     */
    FileShareThinVO getFileShareThinVO(String shareId);

    /**
     * 获取分享文件列表
     *
     * @param shareId  分享id
     * @param parentId 父目录id
     * @return
     */
    List<FileVO> getShareFileItems(String shareId, String parentId);

    /**
     * 下载文件
     *
     * @param shareId 分享id
     * @param fileId  文件id
     */
    FileDownloadVO downloadFiles(String shareId, String fileId);

    /**
     * 创建分享文件夹下载任务
     */
    FolderDownloadTaskVO createFolderDownloadTask(String shareId, String folderId);

    /**
     * 查询分享文件夹下载任务
     */
    FolderDownloadTaskVO getFolderDownloadTask(String shareId, String taskId);

    /**
     * 取消分享文件夹下载打包任务
     */
    void cancelFolderDownloadTask(String shareId, String taskId);

    /**
     * 下载分享文件夹压缩包
     */
    FileDownloadVO downloadFolderTaskFile(String shareId, String taskId);
}
