package com.xddcodec.fs.file.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.xddcodec.fs.file.domain.FileInfo;
import com.xddcodec.fs.framework.common.utils.DateUtils;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AutoMapper(target = FileInfo.class)
public class FileVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文件ID
     */
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
     * 用户ID
     */
    private String userId;

    /**
     * 缩略图URL
     */
    private String thumbnailUrl;

    /**
     * 上传时间
     */
    @JsonFormat(pattern = DateUtils.DATE_TIME_PATTERN)
    private LocalDateTime uploadTime;

    /**
     * 修改时间
     */
    @JsonFormat(pattern = DateUtils.DATE_TIME_PATTERN)
    private LocalDateTime updateTime;

    /**
     * 最后访问时间
     */
    @JsonFormat(pattern = DateUtils.DATE_TIME_PATTERN)
    private LocalDateTime lastAccessTime;

    /**
     * 是否收藏
     */
    private Boolean isFavorite;
}
