/**
 * 本文件定义修改 Bug 个人模板的受限请求参数。
 * 工作空间、创建人和来源 Bug 属于不可变归属信息，不允许通过该请求修改。
 */
package com.wjfz.bugloop.bug.template.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.wjfz.bugloop.bug.entity.BugPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 修改 Bug 个人模板请求。
 *
 * @param name 新模板展示名称，最长 100 个字符
 * @param title 新的默认 Bug 标题，最长 200 个字符
 * @param descriptionMd 新的 Markdown 描述原文
 * @param priority 新的默认优先级
 * @param sortOrder 新的模板排序值，数值越小越靠前
 */
public record UpdateBugTemplateRequest(
        @NotBlank(message = "模板名称不能为空")
        @Size(max = 100, message = "模板名称不能超过 100 个字符")
        String name,
        @NotBlank(message = "模板标题不能为空")
        @Size(max = 200, message = "模板标题不能超过 200 个字符")
        String title,
        @NotBlank(message = "模板描述不能为空")
        String descriptionMd,
        @NotNull(message = "模板默认优先级不能为空")
        BugPriority priority,
        @NotNull(message = "模板排序值不能为空")
        Integer sortOrder) {

    /**
     * 拒绝协议外字段，防止调用方修改模板归属、来源 Bug 或其他服务端维护字段。
     *
     * @param name 未声明字段名
     * @param value 未声明字段值
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("请求包含不支持的字段");
    }
}
