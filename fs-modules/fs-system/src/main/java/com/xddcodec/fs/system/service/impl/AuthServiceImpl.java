package com.xddcodec.fs.system.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.RandomUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.xddcodec.fs.framework.common.constant.CommonConstant;
import com.xddcodec.fs.framework.common.constant.RedisKey;
import com.xddcodec.fs.framework.common.utils.I18nUtils;
import com.xddcodec.fs.framework.notify.mail.domain.Mail;
import com.xddcodec.fs.framework.notify.mail.event.MailEvent;
import com.xddcodec.fs.framework.redis.repository.RedisRepository;
import com.xddcodec.fs.log.annotation.LoginLog;
import com.xddcodec.fs.system.auth.LoginStrategy;
import com.xddcodec.fs.system.auth.LoginStrategyFactory;
import com.xddcodec.fs.system.domain.SysUser;
import com.xddcodec.fs.system.domain.dto.LoginCmd;
import com.xddcodec.fs.system.domain.vo.LoginResult;
import com.xddcodec.fs.system.service.AuthService;
import com.xddcodec.fs.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

import static com.xddcodec.fs.system.domain.table.SysUserTableDef.SYS_USER;

/**
 * 认证服务实现
 *
 * @Author: xddcode
 * @Date: 2024/10/16 14:26
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final LoginStrategyFactory loginStrategyFactory;
    private final SysUserService sysUserService;
    private final RedisRepository redisRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @LoginLog()
    public LoginResult doLogin(LoginCmd cmd) {
        LoginStrategy strategy = loginStrategyFactory.getStrategy(cmd.getLoginType());
        LoginResult result = strategy.authenticate(cmd);

        StpUtil.login(result.getId(), cmd.getIsRemember());
        StpUtil.getSession().set("username", result.getUsername());
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        result.setAccessToken(tokenInfo.getTokenValue());
        //修改最后登录时间
        SysUser user = new SysUser();
        user.setId(result.getId());
        user.setLastLoginAt(LocalDateTime.now());
        sysUserService.updateById(user);
        return result;
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public void sendLoginEmailCode(String account) {
        String normalizedAccount = account.trim().toLowerCase(Locale.ROOT);
        enforceVerificationCodeRateLimit("login", normalizedAccount);

        SysUser user = sysUserService.getByMail(normalizedAccount);
        if (user == null) {
            // 对不存在的账号同样返回成功，避免泄露账号是否已注册。
            return;
        }

        // 生成验证码
        String code = RandomUtil.randomNumbers(CommonConstant.VERIFY_CODE_LENGTH);
        String redisKey = RedisKey.getLoginKey(normalizedAccount);
        redisRepository.setExpire(redisKey, code, RedisKey.VERIFY_CODE_EXPIRE_SECONDS);

        // 发送邮件
        Mail mail = Mail.buildVerifyCodeMail(user.getEmail(), user.getNickname(), code);
        eventPublisher.publishEvent(new MailEvent(this, mail));
    }

    private void enforceVerificationCodeRateLimit(String scene, String email) {
        String rateLimitKey = RedisKey.getVerifyCodeRateLimitKey(scene, email);
        Boolean acquired = redisRepository.setIfAbsent(
                rateLimitKey,
                "1",
                RedisKey.VERIFY_CODE_SEND_INTERVAL_SECONDS
        );
        if (!Boolean.TRUE.equals(acquired)) {
            throw new com.xddcodec.fs.framework.common.exception.BusinessException(
                    I18nUtils.getMessage("user.verification.code.too.frequent")
            );
        }
    }
}
