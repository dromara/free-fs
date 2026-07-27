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
public class FileDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文件ID
     */
    private String id;
    /**
     * 文件名曾
     */
    private String displayName;
    /**
     * 文件后缀
     */
    private String suffix;
    /**
     * 文件大小
     */
    private Long size;
    /**
     * 包含文件数（文件夹详情返回）
     */
    private Integer includeFiles;
    /**
     * 包含文件夹数（文件夹详情返回）
     */
    private Integer includeFolders;
    /**
     * 是否文件夹
     */
    private Boolean isDir;

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
     * 最后访问时间
     */
    @JsonFormat(pattern = DateUtils.DATE_TIME_PATTERN)
    private LocalDateTime lastAccessTime;
}
