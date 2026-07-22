package com.xddcodec.fs.file.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 文件复制请求。
 *
 * <p>dirId 为空表示复制到工作空间根目录。</p>
 */
@Data
public class CopyFileCmd {

    /** 目标目录 ID，空值表示根目录。 */
    private String dirId;

    @NotEmpty(message = "请选择要复制的文件")
    private List<String> fileIds;
}
