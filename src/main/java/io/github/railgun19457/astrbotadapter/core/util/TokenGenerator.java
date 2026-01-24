package io.github.railgun19457.astrbotadapter.core.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Token生成器
 * 用于生成安全的认证Token
 */
public class TokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * 生成指定长度的随机Token
     * @param length Token长度
     * @return 生成的Token
     */
    public static String generate(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    /**
     * 生成32位随机Token
     * @return 32位Token
     */
    public static String generate() {
        return generate(32);
    }

    /**
     * 生成Base64编码的随机Token
     * @param byteLength 原始字节长度
     * @return Base64编码的Token
     */
    public static String generateBase64(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 生成UUID格式的Token（不含连字符）
     * @return UUID格式Token
     */
    public static String generateUUID() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
