package com.xddcodec.fs.system.auth.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.xddcodec.fs.framework.common.enums.LoginType;
import com.xddcodec.fs.framework.common.exception.BusinessException;
import com.xddcodec.fs.framework.common.utils.I18nUtils;
import com.xddcodec.fs.system.auth.LoginStrategy;
import com.xddcodec.fs.system.auth.PasswordHashService;
import com.xddcodec.fs.system.domain.SysUser;
import com.xddcodec.fs.system.domain.dto.LoginCmd;
import com.xddcodec.fs.system.domain.vo.LoginResult;
import com.xddcodec.fs.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.xddcodec.fs.system.domain.table.SysUserTableDef.SYS_USER;

/**
 * 账号密码登录策略
 *
 * @Author: xddcode
 * @Date: 2026/4/2 09:59
 */
@Component
@RequiredArgsConstructor
public class PasswordLoginStrategy implements LoginStrategy {

    private final SysUserMapper userMapper;

    private final PasswordHashService passwordHashService;

    private final static String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";

    @Override
    public LoginType getLoginType() {
        return LoginType.password;
    }

    @Override
    public LoginResult authenticate(LoginCmd cmd) {
        String account = cmd.getAccount();
        String password = cmd.getPassword();
        QueryWrapper queryWrapper = new QueryWrapper();
        if (account.matches(emailRegex)) {
            queryWrapper.where(SYS_USER.EMAIL.eq(account));
        } else {
            queryWrapper.where(SYS_USER.USERNAME.eq(account));
        }
        SysUser user = userMapper.selectOneByQuery(queryWrapper);
        if (user == null) {
            throw new BusinessException(I18nUtils.getMessage("user.account.or.password.incorrect"));
        }
        if (user.getStatus() == 1) {
            throw new BusinessException(I18nUtils.getMessage("user.disabled"));
        }
        if (!passwordHashService.matches(password, user.getPassword())) {
            throw new BusinessException(I18nUtils.getMessage("user.account.or.password.incorrect"));
        }

        if (passwordHashService.isLegacyHash(user.getPassword())) {
            user.setPassword(passwordHashService.encode(password));
        }

        LoginResult loginResult = new LoginResult();
        loginResult.setId(user.getId());
        loginResult.setUsername(user.getUsername());
        //修改最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.update(user);
        return loginResult;
    }
}
