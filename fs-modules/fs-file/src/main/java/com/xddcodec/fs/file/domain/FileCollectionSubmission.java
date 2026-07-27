package com.xddcodec.fs.file.domain;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import com.xddcodec.fs.file.enums.FileCollectionSubmissionStatus;
import com.xddcodec.fs.framework.orm.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("file_collection_submissions")
public class FileCollectionSubmission extends BaseEntity {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.ulid)
    private String id;

    private String collectionId;

    private String submitterName;

    private String submitterIp;

    private String userAgent;

    private String folderId;

    private String uploadTokenHash;

    private Integer fileCount;

    private Long totalSize;

    private FileCollectionSubmissionStatus status;

    private LocalDateTime completedAt;
}
