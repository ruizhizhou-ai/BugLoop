/** 本文件定义验收意见，通过可为空，驳回必填由服务按专用错误码校验，由 BugController 校验并交给服务处理。 */
package com.wjfz.bugloop.bug.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/** 验收意见，通过可为空，驳回必填由服务按专用错误码校验。 */
public record AcceptanceRequest(
        String commentMd) {

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
