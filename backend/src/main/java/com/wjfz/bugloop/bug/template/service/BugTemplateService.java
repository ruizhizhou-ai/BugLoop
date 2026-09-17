/**
 * 本文件实现系统与个人 Bug 模板的创建、查询、修改、共享和逻辑删除。
 * 系统模板全局可用且仅系统管理员可管理；个人模板按工作空间隔离，共享仅扩大成员的使用范围。
 */
package com.wjfz.bugloop.bug.template.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wjfz.bugloop.bug.template.dto.CreateBugTemplateRequest;
import com.wjfz.bugloop.bug.template.dto.SaveBugAsTemplateRequest;
import com.wjfz.bugloop.bug.template.dto.UpdateBugTemplateRequest;
import com.wjfz.bugloop.bug.template.dto.UpdateBugTemplateSharingRequest;
import com.wjfz.bugloop.bug.entity.Bug;
import com.wjfz.bugloop.bug.mapper.BugMapper;
import com.wjfz.bugloop.bug.template.entity.BugTemplate;
import com.wjfz.bugloop.bug.template.mapper.BugTemplateMapper;
import com.wjfz.bugloop.bug.template.vo.BugTemplateVO;
import com.wjfz.bugloop.common.exception.BusinessException;
import com.wjfz.bugloop.workspace.service.WorkspaceAccess;
import com.wjfz.bugloop.workspace.service.WorkspaceAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Bug 模板业务服务，集中保证系统模板权限与个人模板可见、共享和管理边界。
 */
@Service
public class BugTemplateService {

    private static final String SYSTEM_SCOPE = "SYSTEM";
    private static final String PERSONAL_SCOPE = "PERSONAL";

    private final BugTemplateMapper templates;
    private final BugMapper bugs;
    private final WorkspaceAccessService workspaces;

    /**
     * 注入模板持久化入口、来源 Bug 查询入口和工作空间访问校验服务。
     *
     * @param templates 模板 Mapper
     * @param bugs Bug Mapper，只用于把来源 Bug 编号带给列表展示
     * @param workspaces 工作空间访问服务
     */
    public BugTemplateService(BugTemplateMapper templates, BugMapper bugs, WorkspaceAccessService workspaces) {
        this.templates = templates;
        this.bugs = bugs;
        this.workspaces = workspaces;
    }

    /**
     * 查询当前用户在指定工作空间内可使用的模板：全局系统模板、自己的个人模板和他人主动共享的个人模板。
     *
     * @param workspaceId 目标工作空间主键
     * @return 当前用户可使用的模板列表，按范围和排序值排列
     */
    @Transactional(readOnly = true)
    public List<BugTemplateVO> list(Long workspaceId) {
        WorkspaceAccess access = workspaces.requireReadable(workspaceId);
        List<BugTemplate> records = templates.selectList(Wrappers.<BugTemplate>lambdaQuery()
                // 系统模板全局可用；个人模板必须属于当前空间，且仅本人或主动共享的记录可见。
                .and(query -> query.eq(BugTemplate::getScope, SYSTEM_SCOPE)
                        .or(personal -> personal.eq(BugTemplate::getScope, PERSONAL_SCOPE)
                                .eq(BugTemplate::getWorkspaceId, workspaceId)
                                .and(visibility -> visibility.eq(BugTemplate::getCreatorId, access.currentUser().getId())
                                        .or()
                                        .eq(BugTemplate::getShared, true))))
                .orderByAsc(BugTemplate::getScope)
                .orderByAsc(BugTemplate::getSortOrder)
                .orderByDesc(BugTemplate::getUpdatedAt)
                .orderByDesc(BugTemplate::getId));
        Map<Long, String> sourceBugNos = sourceBugNos(records);
        return records.stream()
                .map(template -> toVO(template, sourceBugNos))
                .toList();
    }

    /**
     * 组装模板响应；手工创建的模板没有来源，不查询来源编号。
     *
     * @param template 模板实体
     * @param sourceBugNos 同一批次已查出的来源编号映射
     * @return 模板响应对象
     */
    private BugTemplateVO toVO(BugTemplate template, Map<Long, String> sourceBugNos) {
        return BugTemplateVO.from(template,
                template.getSourceBugId() == null ? null : sourceBugNos.get(template.getSourceBugId()));
    }

    /**
     * 为当前登录用户创建个人模板；工作空间和创建人均从可信服务端上下文填充。
     *
     * @param workspaceId 目标工作空间主键
     * @param request 可复用的模板基础内容
     * @return 已持久化的个人模板
     */
    @Transactional
    public BugTemplateVO create(Long workspaceId, CreateBugTemplateRequest request) {
        WorkspaceAccess access = workspaces.requireWritableMemberForUpdate(workspaceId);
        BugTemplate template = new BugTemplate();
        template.setWorkspaceId(workspaceId);
        template.setCreatorId(access.currentUser().getId());
        template.setScope(PERSONAL_SCOPE);
        template.setShared(false);
        template.setName(request.name().trim());
        template.setTitle(request.title().trim());
        // Markdown 原文需保持用户书写的换行和缩进，不能像标题一样统一 trim。
        template.setDescriptionMd(request.descriptionMd());
        template.setPriority(request.priority());
        template.setSortOrder(0);
        templates.insert(template);
        return BugTemplateVO.from(template, null);
    }

    /**
     * 从已完成权限校验的 Bug 创建当前操作者的个人模板。
     * 仅显式写入请求中的标题、Markdown 描述和优先级，以及来源 Bug 标识；
     * 负责人、验收人、状态、附件和各类历史记录不会参与模板构造。
     *
     * @param sourceBug 已确认属于当前工作空间的来源 Bug
     * @param creatorId 当前操作者用户 ID，也是新个人模板的唯一创建人
     * @param request 模板名称及允许保存的基础字段
     * @return 已持久化的个人模板
     */
    public BugTemplateVO createFromBug(Bug sourceBug, Long creatorId, SaveBugAsTemplateRequest request) {
        BugTemplate template = new BugTemplate();
        template.setWorkspaceId(sourceBug.getWorkspaceId());
        template.setCreatorId(creatorId);
        template.setScope(PERSONAL_SCOPE);
        template.setShared(false);
        template.setName(request.name().trim());
        // 使用请求中的内容而非整实体复制，确保模板字段白名单不会随着 Bug 字段增加而失效。
        template.setTitle(request.title().trim());
        template.setDescriptionMd(request.descriptionMd());
        template.setPriority(request.priority());
        template.setSourceBugId(sourceBug.getId());
        template.setSortOrder(0);
        templates.insert(template);
        return BugTemplateVO.from(template, sourceBug.getBugNo());
    }

    /**
     * 更新当前用户创建的个人模板，归属工作空间、创建人和来源 Bug 均不可修改。
     *
     * @param templateId 目标模板主键
     * @param request 允许修改的基础字段和排序值
     * @return 更新后的模板
     */
    @Transactional
    public BugTemplateVO update(Long templateId, UpdateBugTemplateRequest request) {
        BugTemplate template = requireManageableTemplate(templateId);
        template.setName(request.name().trim());
        template.setTitle(request.title().trim());
        template.setDescriptionMd(request.descriptionMd());
        template.setPriority(request.priority());
        template.setSortOrder(request.sortOrder());
        templates.updateById(template);
        return toVO(template, sourceBugNos(List.of(template)));
    }

    /**
     * 批量读取模板来源 Bug 的业务编号；手工创建的模板没有来源，不参与查询。
     *
     * @param records 待转换的模板实体
     * @return 来源 Bug 主键到业务编号的映射，无来源时返回空映射
     */
    private Map<Long, String> sourceBugNos(List<BugTemplate> records) {
        List<Long> sourceBugIds = records.stream()
                .map(BugTemplate::getSourceBugId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (sourceBugIds.isEmpty()) {
            return Map.of();
        }
        return bugs.selectBatchIds(sourceBugIds).stream()
                .collect(Collectors.toMap(Bug::getId, Bug::getBugNo));
    }

    /**
     * 逻辑删除当前用户自己的模板，删除后 MyBatis-Plus 查询会自动排除该记录。
     *
     * @param templateId 目标模板主键
     */
    @Transactional
    public void delete(Long templateId) {
        BugTemplate template = requireManageableTemplate(templateId);
        if (templates.deleteById(template.getId()) != 1) {
            // 记录可能在读取后被并发删除，统一按不存在处理，避免误报删除成功。
            throw templateNotFound();
        }
    }

    /**
     * 创建全局系统模板。系统模板不隶属任何工作空间，因此只依赖系统管理员角色校验。
     *
     * @param request 系统模板基础内容
     * @return 已创建的系统模板
     */
    @Transactional
    public BugTemplateVO createSystem(CreateBugTemplateRequest request) {
        requireSystemAdmin();
        BugTemplate template = new BugTemplate();
        // 0 是系统模板的保留归属值，真实的全局性质由 scope 字段表达。
        template.setWorkspaceId(0L);
        template.setCreatorId(0L);
        template.setScope(SYSTEM_SCOPE);
        template.setShared(false);
        template.setName(request.name().trim());
        template.setTitle(request.title().trim());
        template.setDescriptionMd(request.descriptionMd());
        template.setPriority(request.priority());
        template.setSortOrder(0);
        templates.insert(template);
        return BugTemplateVO.from(template, null);
    }

    /**
     * 切换当前用户个人模板的共享状态。系统模板始终全局可见，因此不能使用此入口。
     *
     * @param templateId 目标个人模板主键
     * @param request 新共享状态
     * @return 更新后的模板
     */
    @Transactional
    public BugTemplateVO updateSharing(Long templateId, UpdateBugTemplateSharingRequest request) {
        BugTemplate template = requireOwnedPersonalWritableTemplate(templateId);
        template.setShared(request.shared());
        templates.updateById(template);
        return toVO(template, sourceBugNos(List.of(template)));
    }

    /**
     * 按主键读取未删除模板。系统模板只允许系统管理员管理；个人模板仍仅允许创建人管理。
     *
     * @param templateId 模板主键
     * @return 当前用户拥有且所在空间可写的模板
     */
    private BugTemplate requireManageableTemplate(Long templateId) {
        BugTemplate template = templates.selectById(templateId);
        if (template == null) {
            throw templateNotFound();
        }
        if (SYSTEM_SCOPE.equals(template.getScope())) {
            requireSystemAdmin();
            return template;
        }
        return requireOwnedPersonalWritableTemplate(template);
    }

    /**
     * 按主键读取个人模板，校验模板所属空间可写且创建人正是当前用户。
     * 系统管理员也不能代替用户管理个人模板，保证共享只扩大使用范围而不扩大管理权限。
     *
     * @param templateId 目标模板主键
     * @return 当前用户拥有且所在空间可写的个人模板
     */
    private BugTemplate requireOwnedPersonalWritableTemplate(Long templateId) {
        BugTemplate template = templates.selectById(templateId);
        if (template == null) {
            throw templateNotFound();
        }
        return requireOwnedPersonalWritableTemplate(template);
    }

    /**
     * 复用已读取模板的个人归属校验，避免共享状态更新出现二次读取和校验分叉。
     *
     * @param template 已读取模板
     * @return 已确认归属的个人模板
     */
    private BugTemplate requireOwnedPersonalWritableTemplate(BugTemplate template) {
        if (!PERSONAL_SCOPE.equals(template.getScope())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40301, "系统模板仅允许系统管理员管理");
        }
        WorkspaceAccess access = workspaces.requireWritableMemberForUpdate(template.getWorkspaceId());
        if (!Objects.equals(template.getCreatorId(), access.currentUser().getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40301, "无权操作其他用户的个人模板");
        }
        return template;
    }

    /**
     * 校验当前登录用户为系统管理员，作为系统模板所有写操作的统一安全边界。
     */
    private void requireSystemAdmin() {
        if (!workspaces.isSystemAdmin(workspaces.currentUser())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40301, "仅系统管理员可以管理内置模板");
        }
    }

    /**
     * 构造模板不存在或已逻辑删除时的统一错误，避免向客户端暴露删除前的数据。
     *
     * @return 资源不存在异常
     */
    private BusinessException templateNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, 40404, "Bug 模板不存在");
    }
}
