package com.xddcodec.fs.system.auth;

import cn.dev33.satoken.secure.SaSecureUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PasswordHashService {

    private static final Pattern LEGACY_SHA256 = Pattern.compile("^[0-9a-fA-F]{64}$");
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }
        if (isLegacyHash(encodedPassword)) {
            return SaSecureUtil.sha256(rawPassword).equalsIgnoreCase(encodedPassword);
        }
        return encoder.matches(rawPassword, encodedPassword);
    }

    public boolean isLegacyHash(String encodedPassword) {
        return encodedPassword != null && LEGACY_SHA256.matcher(encodedPassword).matches();
    }
}
