package com.hmdp.shared.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 补偿流重试配置项
 * <p>
 * 对应配置前缀 dewu.compensation.retry，目前只有一个参数：
 * maxRetryCount（最大重试次数，默认 3）。
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "dewu.compensation.retry")
public class CompensationRetryProperties {

    private int maxRetryCount = 3;
}
