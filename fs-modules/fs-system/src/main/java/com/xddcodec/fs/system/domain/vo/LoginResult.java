package com.xddcodec.fs.system.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.xddcodec.fs.system.domain.SysUser;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录用户返回信息
 *
 * @Author: xddcode
 * @Date: 2024/10/16 14:34
 */
@Data
@AutoMapper(target = SysUser.class)
@Schema(description = "登录用户返回信息")
public class LoginResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户id")
    private String id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "访问凭据")
    @JsonIgnore
    private String accessToken;
}
