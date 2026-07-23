package com.xddcodec.fs.file.domain.dto;

import com.xddcodec.fs.file.enums.FileCollectionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateFileCollectionStatusCmd {

    @NotNull(message = "收集状态不能为空")
    private FileCollectionStatus status;
}
