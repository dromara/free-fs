package com.xddcodec.fs.storage.domain;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 存储平台表
 *
 * @Author: xddcode
 * @Date: 2024/10/25 14:30
 */
@Data
@Table("storage_platform")
public class StoragePlatform implements Serializable {

    @Serial
    private static final long serialVersionUID = 377103726063614716L;

    /**
     * 自增id
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 存储平台名称
     */
    private String name;

    /**
     * 存储平台标识符
     */
    private String identifier;

    /**
     * 存储平台配置描述schema
     */
    private String configScheme;

    /**
     * 存储平台图标
     */
    private String icon;

    /**
     * 存储平台链接
     */
    private String link;

    /**
     * 是否默认存储平台 0-否 1-是
     */
    private Integer isDefault;

    /**
     * 存储平台描述
     */
    private String desc;
}
