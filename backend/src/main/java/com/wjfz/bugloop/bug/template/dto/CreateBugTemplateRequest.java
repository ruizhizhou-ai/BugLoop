/**
 * 本文件定义手工创建 Bug 个人模板的请求参数。
 * 工作空间和创建人必须从服务端上下文获取，因此请求体只接收可复用的模板内容。
 */
package com.wjfz.bugloop.bug.template.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.wjfz.bugloop.bug.entity.BugPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 手工创建 Bug 个人模板请求。
 *
 * @param name 模板展示名称，最长 100 个字符
 * @param title 创建 Bug 时预填的标题，最长 200 个字符
 * @param descriptionMd 创建 Bug 时预填的 Markdown 描述原文
 * @param priority 创建 Bug 时预填的默认优先级
 */
public record CreateBugTemplateRequest(
        @NotBlank(message = "模板名称不能为空")
        @Size(max = 100, message = "模板名称不能超过 100 个字符")
        String name,
        @NotBlank(message = "模板标题不能为空")
        @Size(max = 200, message = "模板标题不能超过 200 个字符")
        String title,
        @NotBlank(message = "模板描述不能为空")
        String descriptionMd,
        @NotNull(message = "模板默认优先级不能为空")
        BugPriority priority) {

    /**
     * 拒绝协议外字段，防止调用方伪造工作空间、创建人或其他服务端维护字段。
     *
     * @param name 未声明字段名
     * @param value 未声明字段值
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("请求包含不支持的字段");
    }
}
