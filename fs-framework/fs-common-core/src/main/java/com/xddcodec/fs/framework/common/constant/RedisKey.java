package com.xddcodec.fs.framework.common.constant;

/**
 * Redis Key 工具类
 */
public class RedisKey {

    private static final String BASE_KEY = "fs";
    private static final String SEPARATOR = ":";

    /**
     * 验证码过期时间（5分钟）
     */
    public static final long VERIFY_CODE_EXPIRE_SECONDS = 5 * 60;

    /**
     * 验证码发送间隔（60秒）
     */
    public static final long VERIFY_CODE_SEND_INTERVAL_SECONDS = 60;

    /**
     * 缓存过期时间（24小时）
     */
    public static final long CACHE_EXPIRE_SECONDS = 24 * 60 * 60;

    /**
     * 分布式锁过期时间（30秒）
     */
    public static final long LOCK_EXPIRE_SECONDS = 30;

    /**
     * 预览token过期 key
     */
    private static final String PREVIEW_TOKEN_KEY = "preview:token";

    private static final String PREVIEW_WORKSPACE_KEY = "preview:workspace";

    /**
     * 预览token过期时间 默认5分钟
     */
    public static final long PREVIEW_TOKEN_EXPIRE = 5 * 60;

    /**
     * 获取验证码key
     *
     * @param prefix 前缀
     * @param email  邮箱
     * @return fs:code:邮箱
     */
    public static String getVerifyCodeKey(String prefix, String email) {
        return String.join(SEPARATOR, BASE_KEY, prefix, email);
    }

    /**
     * 获取登录redis key
     *
     * @param email
     * @return
     */
    public static String getLoginKey(String email) {
        return getVerifyCodeKey("login", email);
    }

    /**
     * 获取忘记密码redis key
     *
     * @param email
     * @return
     */
    public static String getForgetPasswordKey(String email) {
        return getVerifyCodeKey("forgetPassword", email);
    }

    /**
     * 获取更新邮箱redis key
     *
     * @param email
     * @return
     */
    public static String getUpdateMailKey(String email) {
        return getVerifyCodeKey("updateMail", email);
    }

    /**
     * 获取验证码发送限流 key
     *
     * @param scene 验证码使用场景
     * @param email 邮箱
     * @return fs:codeRate:场景:邮箱
     */
    public static String getVerifyCodeRateLimitKey(String scene, String email) {
        return String.join(SEPARATOR, BASE_KEY, "codeRate", scene, email);
    }

    /**
     * 获取验证码key
     *
     * @param token 预览短链token
     * @return fs:preview:12df12312312312
     */
    public static String getPreviewTokenKey(String token) {
        return String.join(SEPARATOR, BASE_KEY, PREVIEW_TOKEN_KEY, token);
    }

    public static String getPreviewWorkspaceKey(String token) {
        return String.join(SEPARATOR, BASE_KEY, PREVIEW_WORKSPACE_KEY, token);
    }
}
