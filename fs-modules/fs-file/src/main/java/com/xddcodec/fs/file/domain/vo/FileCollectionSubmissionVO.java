package com.xddcodec.fs.file.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.xddcodec.fs.file.enums.FileCollectionSubmissionStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileCollectionSubmissionVO {
    private String id;
    private String submitterName;
    private String submitterIp;
    private String folderId;
    private Integer fileCount;
    private Long totalSize;
    private FileCollectionSubmissionStatus status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;
}
