/**
 * 本文件定义 Bug 个人模板对外响应，仅暴露模板选择和管理所需的持久化字段。
 * 逻辑删除标记属于内部实现细节，不进入正常接口响应。
 */
package com.wjfz.bugloop.bug.template.vo;

import com.wjfz.bugloop.bug.entity.BugPriority;
import com.wjfz.bugloop.bug.template.entity.BugTemplate;

import java.time.LocalDateTime;

/**
 * Bug 个人模板响应对象。
 *
 * @param id 模板主键
 * @param workspaceId 所属工作空间 ID
 * @param creatorId 模板创建人用户 ID
 * @param name 模板展示名称
 * @param title 创建 Bug 时预填的默认标题
 * @param descriptionMd 创建 Bug 时预填的 Markdown 描述原文
 * @param priority 创建 Bug 时预填的默认优先级
 * @param sourceBugId 来源 Bug ID，手工创建时为空
 * @param sourceBugNo 来源 Bug 的业务编号，手工创建或来源缺失时为空
 * @param sortOrder 模板排序值，数值越小越靠前
 * @param createdAt 模板创建时间
 * @param updatedAt 模板最后更新时间
 */
public record BugTemplateVO(
        Long id,
        Long workspaceId,
        Long creatorId,
        String name,
        String title,
        String descriptionMd,
        BugPriority priority,
        Long sourceBugId,
        String sourceBugNo,
        Integer sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * 将已通过归属校验的模板实体转换为接口响应，避免向客户端暴露内部逻辑删除标记。
     *
     * @param template Bug 个人模板实体
     * @param sourceBugNo 来源 Bug 的业务编号，无来源时传 null
     * @return 模板响应对象
     */
    public static BugTemplateVO from(BugTemplate template, String sourceBugNo) {
        return new BugTemplateVO(template.getId(), template.getWorkspaceId(), template.getCreatorId(),
                template.getName(), template.getTitle(), template.getDescriptionMd(), template.getPriority(),
                template.getSourceBugId(), sourceBugNo, template.getSortOrder(), template.getCreatedAt(),
                template.getUpdatedAt());
    }
}
