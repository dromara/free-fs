package com.xddcodec.fs.file.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.xddcodec.fs.file.enums.FileCollectionStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileCollectionVO {
    private String id;
    private String collectionName;
    private String description;
    private String targetFolderId;
    private String targetFolderName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;
    private Boolean permanent;
    private Boolean expired;
    private Boolean hasAccessCode;
    /** 仅创建成功时返回一次，列表和详情不会返回。 */
    private String accessCode;
    private Long maxFileSize;
    private String allowedExtensions;
    private FileCollectionStatus status;
    private Integer submissionCount;
    private Integer fileCount;
    private Long totalSize;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
