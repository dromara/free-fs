package com.xddcodec.fs.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.xddcodec.fs.framework.common.constant.CommonConstant;
import com.xddcodec.fs.framework.common.constant.RedisKey;
import com.xddcodec.fs.framework.common.exception.BusinessException;
import com.xddcodec.fs.framework.common.utils.I18nUtils;
import com.xddcodec.fs.framework.notify.mail.domain.Mail;
import com.xddcodec.fs.framework.notify.mail.event.MailEvent;
import com.xddcodec.fs.framework.redis.repository.RedisRepository;
import com.xddcodec.fs.storage.plugin.boot.StoragePluginManager;
import com.xddcodec.fs.storage.plugin.core.IStorageOperationService;
import com.xddcodec.fs.system.domain.SysUser;
import com.xddcodec.fs.system.domain.dto.*;
import com.xddcodec.fs.system.domain.vo.SysUserVO;
import com.xddcodec.fs.system.mapper.SysUserMapper;
import com.xddcodec.fs.system.auth.PasswordHashService;
import com.xddcodec.fs.system.service.SysUserService;
import com.xddcodec.fs.system.service.SysUserTransferSettingService;
import com.xddcodec.fs.system.service.SysWorkspaceInvitationService;
import io.github.linpeilie.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;
import java.util.Locale;

import static com.xddcodec.fs.system.domain.table.SysUserTableDef.SYS_USER;

/**
 * 用户表 服务实现类
 *
 * @Author: xddcode
 * @Date: 2024/6/7 11:14
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final Converter converter;

    private final RedisRepository redisRepository;

    private final ApplicationEventPublisher eventPublisher;

    private final CacheManager cacheManager;

    private final SysUserTransferSettingService userTransferSettingService;

    private final SysWorkspaceInvitationService workspaceInvitationService;

    private final StoragePluginManager pluginManager;

    private final PasswordHashService passwordHashService;

    @Value("${spring.application.name:free-fs}")
    private String applicationName;

    @Override
    public SysUser getByUsername(String username) {

        return this.getOne(new QueryWrapper().where(SYS_USER.USERNAME.eq(username)));
    }

    @Override
    public SysUser getByMail(String email) {

        return this.getOne(new QueryWrapper().where(SYS_USER.EMAIL.eq(email)));
    }

    @Override
    @Cacheable(value = "user", keyGenerator = "userKeyGenerator")
    public SysUserVO getDetail() {
        String userId = StpUtil.getLoginIdAsString();
        SysUser user = this.getById(userId);
        SysUserVO userVO = converter.convert(user, SysUserVO.class);
        if (user != null) {
            // 设置用户是否已设置密码
            userVO.setIsSetPassword(user.getPassword() != null);
        }
        return userVO;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void register(UserRegisterCmd cmd) {
        SysUser user = this.getByUsername(cmd.getUsername());
        if (user != null) {
            throw new BusinessException(I18nUtils.getMessage("user.username.exists"));
        }
        if (!cmd.getPassword().equals(cmd.getConfirmPassword())) {
            throw new BusinessException(I18nUtils.getMessage("user.password.not.match"));
        }
        user = new SysUser();
        user.setUsername(cmd.getUsername());
        user.setPassword(passwordHashService.encode(cmd.getPassword()));
        user.setEmail(cmd.getEmail().trim().toLowerCase(Locale.ROOT));
        user.setNickname(cmd.getNickname());
        user.setAvatar(cmd.getAvatar());
        this.save(user);

        // 初始化用户传输配置
        userTransferSettingService.initUserTransferSetting(user.getId());

        // 处理邀请令牌
        if (cmd.getInviteToken() != null && !cmd.getInviteToken().isBlank()) {
            workspaceInvitationService.acceptInvitation(cmd.getInviteToken(), user.getId());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    @CacheEvict(value = "user", keyGenerator = "userKeyGenerator")
    public void editUserInfo(UserEditInfoCmd cmd) {
        String userId = StpUtil.getLoginIdAsString();
        SysUser existUser = this.getById(userId);
        if (existUser == null) {
            throw new BusinessException(I18nUtils.getMessage("user.not.exist"));
        }
        existUser.setNickname(cmd.getNickname());
        this.updateById(existUser);
    }

    @Override
    public void sendUpdateMailCode(String mail) {
        String normalizedMail = mail.trim().toLowerCase(Locale.ROOT);
        enforceVerificationCodeRateLimit("updateMail", normalizedMail);

        String userId = StpUtil.getLoginIdAsString();
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(I18nUtils.getMessage("user.not.exist"));
        }
        SysUser existUser = this.getOne(new QueryWrapper().where(SYS_USER.EMAIL.eq(normalizedMail)));
        if (existUser != null && !existUser.getId().equals(user.getId())) {
            throw new BusinessException(I18nUtils.getMessage("user.email.exists"));
        }
        String code = RandomUtil.randomNumbers(CommonConstant.VERIFY_CODE_LENGTH);
        String redisKey = RedisKey.getUpdateMailKey(normalizedMail);
        redisRepository.setExpire(redisKey, code, RedisKey.VERIFY_CODE_EXPIRE_SECONDS);

        Mail mailObj = Mail.buildVerifyCodeMail(normalizedMail, user.getNickname(), code);
        eventPublisher.publishEvent(new MailEvent(this, mailObj));
    }

    @Override
    public void updateMail(UserEditMailCmd cmd) {
        String userId = StpUtil.getLoginIdAsString();
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(I18nUtils.getMessage("user.not.exist"));
        }
        String email = cmd.getEmail().trim().toLowerCase(Locale.ROOT);
        SysUser existUser = this.getOne(new QueryWrapper().where(SYS_USER.EMAIL.eq(email)));
        if (existUser != null && !existUser.getId().equals(user.getId())) {
            throw new BusinessException(I18nUtils.getMessage("user.email.exists"));
        }
        String code = cmd.getCode();
        String redisKey = RedisKey.getUpdateMailKey(email);
        String redisCode = (String) redisRepository.get(redisKey);
        if (!code.equals(redisCode)) {
            throw new BusinessException(I18nUtils.getMessage("user.verification.code.incorrect"));
        }

        user.setEmail(email);
        this.updateById(user);
        redisRepository.del(redisKey);

        Cache userCache = cacheManager.getCache("user");
        if (userCache != null) {
            userCache.evict(user.getId());
        }
    }

    @Override
    public void uploadAvatar(MultipartFile file) {
        String userId = StpUtil.getLoginIdAsString();
        SysUser existUser = this.getById(userId);
        if (existUser == null) {
            throw new BusinessException(I18nUtils.getMessage("user.not.exist"));
        }

        String avatarUrl;
        try {
            IStorageOperationService storageOperationService = pluginManager.getLocalInstance();
            // 优化路径拼接与命名，防止路径穿越
            String suffix = FileUtil.getSuffix(file.getOriginalFilename());
            String fileName = userId + "_" + System.currentTimeMillis() + "." + suffix;
            String avatarPath = applicationName + CommonConstant.AVATAR_SAVE_PATH + "/" + userId;

            // 目录创建逻辑可以封装在 storageOperationService 内部
            String objectKey = avatarPath + "/" + fileName;

            storageOperationService.uploadFile(file.getInputStream(), objectKey);
            avatarUrl = storageOperationService.getFileUrl(objectKey, null);
        } catch (Exception e) {
            throw new BusinessException(I18nUtils.getMessage("file.upload.failed"));
        }

        updateUserAvatarInTransaction(existUser, avatarUrl);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateUserAvatarInTransaction(SysUser user, String url) {
        user.setAvatar(url);
        this.updateById(user);
        Objects.requireNonNull(cacheManager.getCache("user")).evict(user.getId());
    }


    @Override
    @CacheEvict(value = "user", keyGenerator = "userKeyGenerator")
    public void updatePassword(PasswordEditCmd cmd) {
        String userId = StpUtil.getLoginIdAsString();
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(I18nUtils.getMessage("user.not.exist"));
        }
        if (!passwordHashService.matches(cmd.getOldPassword(), user.getPassword())) {
            throw new BusinessException(I18nUtils.getMessage("user.password.incorrect"));
        }
        if (!cmd.getNewPassword().equals(cmd.getConfirmPassword())) {
            throw new BusinessException(I18nUtils.getMessage("user.password.not.match"));
        }
        user.setPassword(passwordHashService.encode(cmd.getNewPassword()));
        this.updateById(user);
    }

    @Override
    public void setPassword(PasswordAddCmd cmd) {
        String userId = StpUtil.getLoginIdAsString();
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(I18nUtils.getMessage("user.not.exist"));
        }
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            throw new BusinessException(I18nUtils.getMessage("user.password.already.set"));
        }
        if (!cmd.getNewPassword().equals(cmd.getConfirmPassword())) {
            throw new BusinessException(I18nUtils.getMessage("user.password.not.match"));
        }
        user.setPassword(passwordHashService.encode(cmd.getNewPassword()));
        this.updateById(user);
    }

    @Override
    public void sendForgetPasswordCode(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        enforceVerificationCodeRateLimit("forgetPassword", normalizedEmail);

        SysUser user = this.getOne(new QueryWrapper().where(SYS_USER.EMAIL.eq(normalizedEmail)));
        if (user == null) {
            // 对不存在的账号同样返回成功，避免泄露账号是否已注册。
            return;
        }
        String code = RandomUtil.randomNumbers(CommonConstant.VERIFY_CODE_LENGTH);
        String redisKey = RedisKey.getForgetPasswordKey(normalizedEmail);
        redisRepository.setExpire(redisKey, code, RedisKey.VERIFY_CODE_EXPIRE_SECONDS);

        Mail mail = Mail.buildVerifyCodeMail(normalizedEmail, user.getNickname(), code);
        eventPublisher.publishEvent(new MailEvent(this, mail));
    }

    @Override
    public void updateForgetPassword(PasswordForgetEditCmd cmd) {
        String email = cmd.getMail().trim().toLowerCase(Locale.ROOT);
        String code = cmd.getCode();
        String redisKey = RedisKey.getForgetPasswordKey(email);
        String redisCode = (String) redisRepository.get(redisKey);
        if (!code.equals(redisCode)) {
            throw new BusinessException(I18nUtils.getMessage("user.verification.code.incorrect"));
        }
        SysUser user = this.getOne(new QueryWrapper().where(SYS_USER.EMAIL.eq(email)));
        if (user == null) {
            throw new BusinessException(I18nUtils.getMessage("user.verification.code.incorrect"));
        }
        if (!cmd.getNewPassword().equals(cmd.getConfirmPassword())) {
            throw new BusinessException(I18nUtils.getMessage("user.password.not.match"));
        }
        user.setPassword(passwordHashService.encode(cmd.getNewPassword()));
        this.updateById(user);
        redisRepository.del(redisKey);

        Cache userCache = cacheManager.getCache("user");
        if (userCache != null) {
            userCache.evict(user.getId());
        }
    }

    private void enforceVerificationCodeRateLimit(String scene, String email) {
        String rateLimitKey = RedisKey.getVerifyCodeRateLimitKey(scene, email);
        Boolean acquired = redisRepository.setIfAbsent(
                rateLimitKey,
                "1",
                RedisKey.VERIFY_CODE_SEND_INTERVAL_SECONDS
        );
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(I18nUtils.getMessage("user.verification.code.too.frequent"));
        }
    }
}
