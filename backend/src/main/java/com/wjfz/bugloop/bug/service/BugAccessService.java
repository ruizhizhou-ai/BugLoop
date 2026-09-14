/**
 * 本文件在工作空间授权之上实现 Bug 的字段编辑、有效成员和责任人约束。
 * 管理身份只用于基础信息及人员调整，处理和验收始终要求本人是指定责任人。
 */
package com.wjfz.bugloop.bug.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wjfz.bugloop.bug.entity.Bug;
import com.wjfz.bugloop.common.exception.BusinessException;
import com.wjfz.bugloop.user.entity.User;
import com.wjfz.bugloop.user.service.UserService;
import com.wjfz.bugloop.workspace.entity.WorkspaceMember;
import com.wjfz.bugloop.workspace.mapper.WorkspaceMemberMapper;
import com.wjfz.bugloop.workspace.service.WorkspaceAccess;
import com.wjfz.bugloop.workspace.service.WorkspaceAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.Objects;

/** Bug 权限规则，不直接改变任何业务数据。 */
@Service
public class BugAccessService {
    private final UserService users;
    private final WorkspaceMemberMapper members;
    private final WorkspaceAccessService workspaces;

    /** 注入系统用户、空间成员和统一授权入口。 */
    public BugAccessService(UserService users, WorkspaceMemberMapper members, WorkspaceAccessService workspaces) {
        this.users = users;
        this.members = members;
        this.workspaces = workspaces;
    }

    /**
     * 确认指定人员是空间中的有效成员，创建者、负责人和验收人均使用相同规则。
     * @param workspaceId 当前空间
     * @param userId 指定用户
     * @return 已确认存在且启用的用户
     */
    public User requireActiveMember(Long workspaceId, Long userId) {
        User user = userId == null ? null : users.findById(userId).orElse(null);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40401, "用户不存在");
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40305, "指定用户已被禁用");
        }
        if (members.selectCount(Wrappers.<WorkspaceMember>lambdaQuery()
                .eq(WorkspaceMember::getWorkspaceId, workspaceId).eq(WorkspaceMember::getUserId, userId)) == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 42205, "指定人员不是当前工作空间成员");
        }
        return user;
    }

    /** 校验基础信息编辑权限：管理员或该 Bug 创建者。 */
    public void requireEditor(Bug bug, WorkspaceAccess access) {
        if (!isManager(access) && !Objects.equals(bug.getCreatorId(), access.currentUser().getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40301, "只能修改自己创建的 Bug");
        }
    }

    /** 校验指派和验收人调整权限，普通成员不能改变责任边界。 */
    public void requireManager(WorkspaceAccess access) {
        if (!isManager(access)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40301, "当前用户无 Bug 人员管理权限");
        }
    }

    /** 校验当前操作者为有效负责人；任何管理员身份均不能代替负责人处理或提交。 */
    public void requireAssignee(Bug bug, WorkspaceAccess access) {
        if (bug.getAssigneeId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 42203, "Bug 尚未指定负责人");
        }
        if (!Objects.equals(bug.getAssigneeId(), access.currentUser().getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40303, "当前用户不是 Bug 负责人");
        }
        requireActiveMember(bug.getWorkspaceId(), bug.getAssigneeId());
    }

    /** 校验当前操作者为有效验收人，防止工作空间管理员绕过验收责任。 */
    public void requireAcceptor(Bug bug, WorkspaceAccess access) {
        if (bug.getAcceptorId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 42204, "Bug 尚未指定验收人");
        }
        if (!Objects.equals(bug.getAcceptorId(), access.currentUser().getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40304, "当前用户不是 Bug 验收人");
        }
        requireActiveMember(bug.getWorkspaceId(), bug.getAcceptorId());
    }

    /** 判断是否有基础信息和人员管理权限，不用于状态流转。 */
    private boolean isManager(WorkspaceAccess access) {
        return workspaces.isSystemAdmin(access.currentUser())
                || (access.currentRole() != null && access.currentRole().canManageMembers());
    }
}
