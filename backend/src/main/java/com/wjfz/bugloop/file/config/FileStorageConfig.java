/** 本文件启用附件本地存储的配置属性绑定，使业务服务无需依赖环境变量读取细节。 */
package com.wjfz.bugloop.file.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 本地附件存储配置入口。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
public class FileStorageConfig {
}
