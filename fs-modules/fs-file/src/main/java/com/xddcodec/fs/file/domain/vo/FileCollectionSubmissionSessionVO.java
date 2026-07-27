package com.xddcodec.fs.file.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileCollectionSubmissionSessionVO {
    private String submissionId;
    private String uploadToken;
    private String folderName;
}
