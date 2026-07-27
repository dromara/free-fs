package com.xddcodec.fs.file.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateFileCollectionCmd {

    @NotBlank(message = "收集名称不能为空")
    @Size(max = 255, message = "收集名称不能超过255个字符")
    private String collectionName;

    @Size(max = 1000, message = "收集说明不能超过1000个字符")
    private String description;

    @NotBlank(message = "目标文件夹不能为空")
    private String targetFolderId;

    /** 1-7天，2-30天，3-自定义，4-永久。 */
    private Integer expireType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    private Boolean needAccessCode;

    @Size(max = 32, message = "访问码不能超过32个字符")
    private String accessCode;

    @Positive(message = "单文件大小限制必须大于0")
    @Max(value = 1099511627776L, message = "单文件大小限制不能超过1TB")
    private Long maxFileSize;

    @Size(max = 1000, message = "文件类型限制不能超过1000个字符")
    private String allowedExtensions;
}
