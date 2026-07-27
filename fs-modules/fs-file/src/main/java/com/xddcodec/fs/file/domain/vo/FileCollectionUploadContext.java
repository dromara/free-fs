package com.xddcodec.fs.file.domain.vo;

import com.xddcodec.fs.file.domain.FileCollection;
import com.xddcodec.fs.file.domain.FileCollectionSubmission;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileCollectionUploadContext {
    private FileCollection collection;
    private FileCollectionSubmission submission;
}
