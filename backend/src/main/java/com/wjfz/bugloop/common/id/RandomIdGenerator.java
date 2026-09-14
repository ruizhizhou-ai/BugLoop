/**
 * 本文件为对外暴露为数字的业务主键生成随机 ID，确保 User 和 Workspace 的 ID 不可按创建顺序推测。
 * 生成范围限制在 JavaScript 安全整数以内，使前端以 number 接收时不会丢失精度。
 */
package com.wjfz.bugloop.common.id;

import java.security.SecureRandom;

/**
 * 随机业务主键生成器。
 */
public final class RandomIdGenerator {

    /** JavaScript Number 能无损表达的最大整数。 */
    public static final long MAX_JAVASCRIPT_SAFE_INTEGER = 9_007_199_254_740_991L;

    private static final SecureRandom RANDOM = new SecureRandom();

    private RandomIdGenerator() {
    }

    /**
     * 生成正随机业务主键。
     *
     * @return 位于 JavaScript 安全整数范围内的正 Long
     */
    public static long nextId() {
        return RANDOM.nextLong(1, MAX_JAVASCRIPT_SAFE_INTEGER);
    }
}
