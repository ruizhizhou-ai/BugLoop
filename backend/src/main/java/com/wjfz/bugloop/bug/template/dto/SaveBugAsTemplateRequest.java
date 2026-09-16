/**
 * 本文件定义后续“从 Bug 保存为个人模板”场景的请求参数。
 * 当前仅固定输入契约，不包含 Bug 查询、权限校验或模板保存逻辑。
 */
package com.wjfz.bugloop.bug.template.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.wjfz.bugloop.bug.entity.BugPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 从 Bug 保存为个人模板的请求。
 *
 * @param name 模板展示名称，最长 100 个字符
 * @param title 从来源 Bug 带入或调整后的默认标题
 * @param descriptionMd 从来源 Bug 带入或调整后的 Markdown 描述原文
 * @param priority 从来源 Bug 带入或调整后的默认优先级
 */
public record SaveBugAsTemplateRequest(
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
     * 拒绝协议外字段，来源 Bug、工作空间和创建人均由路径及服务端上下文确定。
     *
     * @param name 未声明字段名
     * @param value 未声明字段值
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("请求包含不支持的字段");
    }
}
