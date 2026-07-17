package com.xddcodec.fs.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码DTO
 *
 * @Author: xddcode
 * @Date: 2024/6/17 17:31
 */
@Data
public class PasswordEditCmd{

    @NotBlank(message = "oldPassword不能为空")
    @Size(max = 128, message = "oldPassword长度不能超过128个字符")
    private String oldPassword;

    @NotBlank(message = "newPassword不能为空")
    @Size(min = 8, max = 128, message = "newPassword长度必须在8到128个字符之间")
    private String newPassword;

    @NotBlank(message = "confirmPassword不能为空")
    @Size(min = 8, max = 128, message = "confirmPassword长度必须在8到128个字符之间")
    private String confirmPassword;
}
