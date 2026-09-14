/** 本文件定义设置验收人参数，只能选择当前空间有效成员，由 BugController 校验并交给服务处理。 */
package com.wjfz.bugloop.bug.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;

/** 设置验收人参数，只能选择当前空间有效成员。 */
public record SetBugAcceptorRequest(
        @NotNull @Positive Long acceptorId) {

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
