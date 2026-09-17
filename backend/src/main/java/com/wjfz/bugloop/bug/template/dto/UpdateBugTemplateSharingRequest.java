/**
 * 本文件定义个人 Bug 模板共享开关的独立更新请求。
 * 将共享状态与模板内容拆分，避免共享按钮覆盖用户正在编辑的标题、描述或排序信息。
 */
package com.wjfz.bugloop.bug.template.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;

/**
 * 个人 Bug 模板共享状态更新请求，仅允许创建人切换该状态。
 *
 * @param shared 是否允许同一工作空间的其他成员使用该模板
 */
public record UpdateBugTemplateSharingRequest(
        @NotNull(message = "模板共享状态不能为空")
        Boolean shared) {

    /**
     * 拒绝协议外字段，确保共享接口不会被用于篡改模板内容或归属。
     *
     * @param name 未声明字段名
     * @param value 未声明字段值
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("请求包含不支持的字段");
    }
}
