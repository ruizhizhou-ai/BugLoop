/** 本文件描述已落盘文件的内部标识，供附件服务写入数据库而不向 API 响应泄露存储路径。 */
package com.wjfz.bugloop.file.service;

/** 本地存储后的文件信息。 */
public record StoredFile(String storageName, String relativePath) {
}
