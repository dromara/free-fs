package com.xddcodec.fs.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册cmd
 *
 * @Author: xddcode
 * @Date: 2024/10/16 16:02
 */
@Data
public class UserRegisterCmd {

    @NotBlank(message = "username不能为空")
    private String username;

    @NotBlank(message = "password不能为空")
    @Size(min = 8, max = 128, message = "password长度必须在8到128个字符之间")
    private String password;

    @NotBlank(message = "confirmPassword不能为空")
    @Size(min = 8, max = 128, message = "confirmPassword长度必须在8到128个字符之间")
    private String confirmPassword;

    @NotBlank(message = "email不能为空")
    private String email;

    @NotBlank(message = "nickname不能为空")
    private String nickname;

    private String avatar;

    /**
     * 邀请令牌（可选）
     */
    private String inviteToken;
}
