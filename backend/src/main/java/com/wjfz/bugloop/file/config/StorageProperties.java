/**
 * 本文件承接 bugloop.storage 配置，向附件存储服务提供唯一的本地根目录。
 * 根目录可由环境变量覆盖，避免将开发机路径或生产存储路径写死在业务代码中。
 */
package com.wjfz.bugloop.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 本地文件存储配置。 */
@ConfigurationProperties(prefix = "bugloop.storage")
public class StorageProperties {
    private String root;

    /** 返回附件存储根目录。 */
    public String getRoot() {
        return root;
    }

    /** 绑定配置文件中的附件存储根目录。 */
    public void setRoot(String root) {
        this.root = root;
    }
}
