package com.xddcodec.fs.system.controller;

import com.xddcodec.fs.framework.common.domain.Result;
import com.xddcodec.fs.system.domain.dto.LoginCmd;
import com.xddcodec.fs.system.domain.vo.LoginResult;
import com.xddcodec.fs.system.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;

@RestController
@RequestMapping("/apis/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理")
public class AuthController {

    private final AuthService authService;

    @Value("${security.auth-cookie.secure:false}")
    private boolean authCookieSecure;

    @Value("${security.auth-cookie.same-site:Strict}")
    private String authCookieSameSite;

    @Value("${sa-token.timeout:86400}")
    private long tokenTimeout;

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<?> doLogin(@Valid @RequestBody LoginCmd cmd, HttpServletResponse response) {
        LoginResult loginResult = authService.doLogin(cmd);
        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from("Authorization", loginResult.getAccessToken())
                .httpOnly(true)
                .secure(authCookieSecure)
                .sameSite(authCookieSameSite)
                .path("/");
        if (Boolean.TRUE.equals(cmd.getIsRemember())) {
            cookieBuilder.maxAge(Duration.ofSeconds(tokenTimeout));
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookieBuilder.build().toString());
        return Result.ok(loginResult);
    }

    @Operation(summary = "登录-发送验证码")
    @PostMapping("/login/email-code")
    public Result<?> sendLoginEmailCode(@RequestParam String account) {
        authService.sendLoginEmailCode(account);
        return Result.ok();
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<?> logout(HttpServletResponse response) {
        authService.logout();
        ResponseCookie expiredCookie = ResponseCookie.from("Authorization", "")
                .httpOnly(true)
                .secure(authCookieSecure)
                .sameSite(authCookieSameSite)
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());
        return Result.ok();
    }
}
