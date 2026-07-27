package com.xddcodec.fs.file.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.xddcodec.fs.file.enums.FileCollectionStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileCollectionPublicVO {
    private String id;
    private String collectionName;
    private String description;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;
    private Boolean expired;
    private Boolean hasAccessCode;
    private Long maxFileSize;
    private String allowedExtensions;
    private FileCollectionStatus status;
}
