package com.xddcodec.fs.file.domain;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import com.xddcodec.fs.file.enums.FileCollectionStatus;
import com.xddcodec.fs.framework.orm.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("file_collections")
public class FileCollection extends BaseEntity {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.ulid)
    private String id;

    private String userId;

    private String workspaceId;

    private String targetFolderId;

    private String storagePlatformSettingId;

    private String collectionName;

    private String description;

    private String accessCodeHash;

    private LocalDateTime expireTime;

    private Long maxFileSize;

    private String allowedExtensions;

    private FileCollectionStatus status;

    private Integer submissionCount;

    private Integer fileCount;

    private Long totalSize;
}
