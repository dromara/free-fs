package com.xddcodec.fs.storage.domain;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.core.keygen.KeyGenerators;
import com.xddcodec.fs.framework.orm.entity.BaseEntity;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 存储平台配置表
 *
 * @Author: xddcode
 * @Date: 2024/10/25 14:30
 */
@Data
@Table("storage_settings")
@EqualsAndHashCode(callSuper = true)
public class StorageSetting extends BaseEntity {

    /**
     * 自增id
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.uuid)
    private String id;

    /**
     * 存储平台标识符
     */
    private String platformIdentifier;

    /**
     * 配置数据
     */
    private String configData;

    /**
     * 是否启用 0-否 1-是
     */
    private Integer enabled;

    /**
     * 所属工作空间ID
     */
    private String workspaceId;

    /**
     * 备注
     */
    private String remark;

    /**
     * 是否逻辑删除 0-否 1-是
     */
    @Column(isLogicDelete = true)
    private Integer deleted;
}
