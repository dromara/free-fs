package com.xddcodec.fs.file.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateFileCollectionSubmissionCmd {

    @NotBlank(message = "提交人姓名不能为空")
    @Size(max = 64, message = "提交人姓名不能超过64个字符")
    private String submitterName;

    @Size(max = 32, message = "访问码不能超过32个字符")
    private String accessCode;
}
