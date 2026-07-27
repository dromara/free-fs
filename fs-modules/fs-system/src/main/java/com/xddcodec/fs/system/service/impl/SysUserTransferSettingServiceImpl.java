package com.xddcodec.fs.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.xddcodec.fs.system.domain.SysUserTransferSetting;
import com.xddcodec.fs.system.domain.dto.UserTransferSettingEditCmd;
import com.xddcodec.fs.system.mapper.SysUserTransferSettingMapper;
import com.xddcodec.fs.system.service.SysUserTransferSettingService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import static com.xddcodec.fs.system.domain.table.SysUserTransferSettingTableDef.SYS_USER_TRANSFER_SETTING;

/**
 * 用户传输设置业务接口实现
 *
 * @Author: xddcode
 * @Date: 2025/11/11 14:35
 */
@Service
public class SysUserTransferSettingServiceImpl extends ServiceImpl<SysUserTransferSettingMapper, SysUserTransferSetting> implements SysUserTransferSettingService {

    @Override
    public void initUserTransferSetting(String userId) {
        SysUserTransferSetting transferSetting = SysUserTransferSetting.init(userId);
        this.save(transferSetting);
    }

    @Override
    @Cacheable(value = "userTransferSetting", keyGenerator = "userKeyGenerator", unless = "#result == null")
    public SysUserTransferSetting getByUser() {
        String userId = StpUtil.getLoginIdAsString();
        SysUserTransferSetting transferSetting = this.getOne(
                new QueryWrapper().where(SYS_USER_TRANSFER_SETTING.USER_ID.eq(userId))
        );
        if (transferSetting == null) {
            transferSetting = SysUserTransferSetting.init(userId);
            this.save(transferSetting);
        }
        return transferSetting;
    }

    @Override
    @CacheEvict(value = "userTransferSetting", keyGenerator = "userKeyGenerator")
    public void updateUserTransferSetting(UserTransferSettingEditCmd cmd) {
        String userId = StpUtil.getLoginIdAsString();

        SysUserTransferSetting transferSetting = this.getOne(
                new QueryWrapper()
                        .where(SYS_USER_TRANSFER_SETTING.USER_ID.eq(userId))
        );
        if (transferSetting == null) {
            SysUserTransferSetting newTransferSetting = SysUserTransferSetting.init(userId);
            this.save(newTransferSetting);
        } else {
            transferSetting.setUserId(userId);
            transferSetting.setDownloadLocation(cmd.getDownloadLocation());
            transferSetting.setIsDefaultDownloadLocation(cmd.getIsDefaultDownloadLocation());
            transferSetting.setConcurrentDownloadQuantity(cmd.getConcurrentDownloadQuantity());
            transferSetting.setConcurrentUploadQuantity(cmd.getConcurrentUploadQuantity());
            transferSetting.setDownloadSpeedLimit(cmd.getDownloadSpeedLimit());
            transferSetting.setChunkSize(cmd.getChunkSize());
            this.updateById(transferSetting);
        }
    }

    @Override
    @CacheEvict(value = "userTransferSetting", key = "#userId")
    public void deleteUserTransferSetting(String userId) {
        this.remove(new QueryWrapper().where(SYS_USER_TRANSFER_SETTING.USER_ID.eq(userId)));
    }

    @Override
    public Long getChunkSize(String userId) {
        SysUserTransferSetting setting = this.getOne(
                new QueryWrapper().where(SYS_USER_TRANSFER_SETTING.USER_ID.eq(userId))
        );
        if (setting != null && setting.getChunkSize() != null && setting.getChunkSize() > 0) {
            return setting.getChunkSize();
        }
        // 默认分片大小：5MB
        return 5L * 1024 * 1024;
    }
}
