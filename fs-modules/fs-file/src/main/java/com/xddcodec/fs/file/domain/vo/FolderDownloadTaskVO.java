package com.xddcodec.fs.file.domain.vo;

import lombok.Data;

@Data
public class FolderDownloadTaskVO {
    private String taskId;
    private String folderId;
    private String folderName;
    private String status;
    private Integer progress;
    private Integer totalFiles;
    private Integer processedFiles;
    private Long totalBytes;
    private Long processedBytes;
    private Long zipSize;
    private String message;
    private String errorMessage;
}
