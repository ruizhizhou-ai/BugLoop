/** 本文件定义基础信息及客户端读取的版本号；拒绝状态和责任人等越权字段，由 BugController 校验并交给服务处理。 */
package com.wjfz.bugloop.bug.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.wjfz.bugloop.bug.entity.BugPriority;
import jakarta.validation.constraints.*;

/** 基础信息及客户端读取的版本号；拒绝状态和责任人等越权字段。 */
public record UpdateBugRequest(
        @NotBlank(message = "标题不能为空") @Size(max = 200, message = "标题最多 200 字符") String title,
        @NotBlank(message = "问题描述不能为空") String descriptionMd,
        @NotNull BugPriority priority,
        @NotNull @PositiveOrZero Integer version) {

    /**
     * 拒绝协议外字段，避免调用者误以为通用接口可以修改状态或工作空间。
     * @param name 未声明字段名
     * @param value 未声明字段值
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("请求包含不支持的字段");
    }
}
