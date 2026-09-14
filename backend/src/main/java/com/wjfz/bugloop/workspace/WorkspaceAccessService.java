/**
 * 本文件集中实现工作空间边界、角色和启停状态校验，是后续所有 Workspace/Bug 数据访问的安全入口。
 */
package com.wjfz.bugloop.workspace;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wjfz.bugloop.common.exception.BusinessException;
import com.wjfz.bugloop.user.User;
import com.wjfz.bugloop.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 工作空间访问服务，将“成员边界”和“角色权限”两层校验封装为可复用方法。
 */
@Service
public class WorkspaceAccessService {

    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper memberMapper;
    private final UserService userService;

    public WorkspaceAccessService(
            WorkspaceMapper workspaceMapper,
            WorkspaceMemberMapper memberMapper,
            UserService userService) {
        this.workspaceMapper = workspaceMapper;
        this.memberMapper = memberMapper;
        this.userService = userService;
    }

    /**
     * 校验当前用户可读取目标工作空间。普通用户必须是成员，SYSTEM_ADMIN 可处理未加入的工作空间。
     *
     * @param workspaceId 工作空间主键
     * @return 已完成校验的访问上下文
     */
    public WorkspaceAccess requireReadable(Long workspaceId) {
        Workspace workspace = findWorkspace(workspaceId);
        return requireMembershipOrSystemAdmin(workspace, currentUser());
    }

    /**
     * 校验当前用户可创建工作空间。SYSTEM_ADMIN、未加入任何空间的用户，
     * 以及已在某个空间担任 OWNER / ADMIN 的用户允许创建；只有 MEMBER 身份的用户不允许。
     *
     * @return 当前系统用户
     */
    public User requireWorkspaceCreator() {
        User user = currentUser();
        if (isSystemAdmin(user)) {
            return user;
        }
        long memberships = memberMapper.selectCount(Wrappers.<WorkspaceMember>lambdaQuery()
                .eq(WorkspaceMember::getUserId, user.getId()));
        if (memberships == 0) {
            return user;
        }
        long managerRoles = memberMapper.selectCount(Wrappers.<WorkspaceMember>lambdaQuery()
                .eq(WorkspaceMember::getUserId, user.getId())
                .in(WorkspaceMember::getRole, WorkspaceRole.OWNER, WorkspaceRole.ADMIN));
        if (managerRoles == 0) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40301, "当前用户无创建工作空间权限");
        }
        return user;
    }

    /**
     * 在事务内锁定工作空间并校验成员管理权限。行锁会串行化成员变更和停用操作，
     * 避免并发降级或删除两个 OWNER 后留下无 OWNER 的工作空间。
     *
     * @param workspaceId 工作空间主键
     * @return 已完成写权限校验的访问上下文
     */
    public WorkspaceAccess requireMemberManagerForUpdate(Long workspaceId) {
        Workspace workspace = lockWorkspace(workspaceId);
        ensureEnabled(workspace);
        User user = currentUser();
        WorkspaceAccess access = requireMembershipOrSystemAdmin(workspace, user);
        if (!isSystemAdmin(user)
                && (access.membership() == null || !access.membership().getRole().canManageMembers())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40301, "当前用户无成员管理权限");
        }
        return access;
    }

    /**
     * 在事务内锁定工作空间并校验 OWNER 权限，用于修改基础信息和停用。
     *
     * @param workspaceId 工作空间主键
     * @return 已完成写权限校验的访问上下文
     */
    public WorkspaceAccess requireOwnerForUpdate(Long workspaceId) {
        Workspace workspace = lockWorkspace(workspaceId);
        ensureEnabled(workspace);
        return requireOwnerOrSystemAdmin(workspace);
    }

    /**
     * 在事务内锁定工作空间并校验 OWNER 权限，但不限制当前启停状态。
     * 该入口只用于启停状态切换，使重新启用能够访问 DISABLED 空间，同时保持角色边界不变。
     *
     * @param workspaceId 工作空间主键
     * @return 已完成权限校验的访问上下文
     */
    public WorkspaceAccess requireOwnerForStatusChange(Long workspaceId) {
        return requireOwnerOrSystemAdmin(lockWorkspace(workspaceId));
    }

    /**
     * 校验当前用户是 OWNER 或 SYSTEM_ADMIN，不附加启停状态条件。
     *
     * @param workspace 已锁定的工作空间
     * @return 已完成权限校验的访问上下文
     */
    private WorkspaceAccess requireOwnerOrSystemAdmin(Workspace workspace) {
        User user = currentUser();
        WorkspaceAccess access = requireMembershipOrSystemAdmin(workspace, user);
        if (!isSystemAdmin(user)
                && (access.membership() == null || access.membership().getRole() != WorkspaceRole.OWNER)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40301, "当前用户无工作空间管理权限");
        }
        return access;
    }

    /**
     * 校验当前用户是目标工作空间成员，供后续 Bug 查询等数据隔离场景复用。
     * SYSTEM_ADMIN 按规范可以越过成员关系处理异常，但仍必须通过此统一入口。
     *
     * @param workspaceId 工作空间主键
     * @return 访问上下文
     */
    public WorkspaceAccess requireMember(Long workspaceId) {
        return requireReadable(workspaceId);
    }

    /**
     * 在事务内锁定工作空间并校验可写成员边界，供后续创建 Bug、状态流转等操作复用。
     * 停用状态会在进入具体 Bug 业务前统一拒绝，避免各业务方法遗漏只读规则。
     *
     * @param workspaceId 工作空间主键
     * @return 已确认空间启用且当前用户可访问的上下文
     */
    public WorkspaceAccess requireWritableMemberForUpdate(Long workspaceId) {
        Workspace workspace = lockWorkspace(workspaceId);
        ensureEnabled(workspace);
        return requireMembershipOrSystemAdmin(workspace, currentUser());
    }

    /**
     * 返回当前登录用户并确认用户数据仍然存在且启用。
     *
     * @return 当前系统用户
     */
    public User currentUser() {
        Long userId = StpUtil.getLoginIdAsLong();
        User user = userService.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.UNAUTHORIZED, 40101, "登录状态已失效，请重新登录"));
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40305, "用户已被禁用");
        }
        return user;
    }

    /**
     * 判断用户是否为系统管理员。
     *
     * @param user 系统用户
     * @return SYSTEM_ADMIN 返回 true
     */
    public boolean isSystemAdmin(User user) {
        return SYSTEM_ADMIN.equals(user.getSystemRole());
    }

    private Workspace findWorkspace(Long workspaceId) {
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40402, "工作空间不存在");
        }
        return workspace;
    }

    private Workspace lockWorkspace(Long workspaceId) {
        Workspace workspace = workspaceMapper.selectByIdForUpdate(workspaceId);
        if (workspace == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40402, "工作空间不存在");
        }
        return workspace;
    }

    private WorkspaceAccess requireMembershipOrSystemAdmin(Workspace workspace, User user) {
        WorkspaceMember member = memberMapper.selectOne(Wrappers.<WorkspaceMember>lambdaQuery()
                .eq(WorkspaceMember::getWorkspaceId, workspace.getId())
                .eq(WorkspaceMember::getUserId, user.getId()));
        if (member == null && !isSystemAdmin(user)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40302, "不是当前工作空间成员");
        }
        return new WorkspaceAccess(workspace, user, member);
    }

    private void ensureEnabled(Workspace workspace) {
        if (workspace.getStatus() == WorkspaceStatus.DISABLED) {
            throw new BusinessException(HttpStatus.CONFLICT, 40901, "工作空间已停用，只允许查看历史数据");
        }
    }
}
