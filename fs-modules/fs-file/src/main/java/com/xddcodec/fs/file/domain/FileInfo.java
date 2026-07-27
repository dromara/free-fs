package com.xddcodec.fs.file.domain;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件资源实体类
 *
 * @Author: xddcode
 * @Date: 2025/5/8 9:18
 */
@Data
@Table("file_info")
public class FileInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 6921917921356128093L;

    /**
     * 主键ID
     */
    @Id(keyType = KeyType.None)
    private String id;

    /**
     * 资源名称
     */
    private String objectKey;

    /**
     * 资源原始名称
     */
    private String originalName;

    /**
     * 资源别名
     */
    private String displayName;

    /**
     * 后缀名
     */
    private String suffix;

    /**
     * 大小
     */
    private Long size;

    /**
     * 存储标准MIME类型
     */
    private String mimeType;

    /**
     * 是否目录
     */
    private Boolean isDir;

    /**
     * 父目录ID
     */
    private String parentId;

    /**
     * 所属工作空间ID
     */
    private String workspaceId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用于秒传和文件校验
     */
    private String contentMd5;

    /**
     * 存储平台标识符
     */
    private String storagePlatformSettingId;

    /**
     * 上传时间
     */
    private LocalDateTime uploadTime;

    /**
     * 修改时间
     */
    private LocalDateTime updateTime;

    /**
     * 最后访问时间
     */
    private LocalDateTime lastAccessTime;

    /**
     * 软删除标记，回收站标识0：未删除 1：已删除
     */
    private Boolean isDeleted;

    /**
     * 删除时间
     */
    private LocalDateTime deletedTime;
}