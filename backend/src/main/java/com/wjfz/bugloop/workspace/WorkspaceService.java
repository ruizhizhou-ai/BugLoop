/**
 * 本文件实现工作空间、成员、角色、切换与启停流程，并在同一事务内记录审计日志。
 */
package com.wjfz.bugloop.workspace;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wjfz.bugloop.common.exception.BusinessException;
import com.wjfz.bugloop.user.User;
import com.wjfz.bugloop.user.UserService;
import com.wjfz.bugloop.workspace.dto.AddWorkspaceMemberRequest;
import com.wjfz.bugloop.workspace.dto.CreateWorkspaceRequest;
import com.wjfz.bugloop.workspace.dto.UpdateWorkspaceMemberRoleRequest;
import com.wjfz.bugloop.workspace.dto.UpdateWorkspaceRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工作空间业务服务，Controller 只负责协议转换，所有权限和一致性规则都在此处执行。
 */
@Service
public class WorkspaceService {

    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper memberMapper;
    private final WorkspaceOperationLogMapper operationLogMapper;
    private final WorkspaceAccessService accessService;
    private final UserService userService;

    public WorkspaceService(
            WorkspaceMapper workspaceMapper,
            WorkspaceMemberMapper memberMapper,
            WorkspaceOperationLogMapper operationLogMapper,
            WorkspaceAccessService accessService,
            UserService userService) {
        this.workspaceMapper = workspaceMapper;
        this.memberMapper = memberMapper;
        this.operationLogMapper = operationLogMapper;
        this.accessService = accessService;
        this.userService = userService;
    }

    /**
     * 创建工作空间，并在同一事务内建立创建者的 OWNER 成员关系和审计日志。
     *
     * @param request 创建参数
     * @return 新工作空间
     */
    @Transactional
    public WorkspaceVO create(CreateWorkspaceRequest request) {
        User currentUser = accessService.currentUser();
        LocalDateTime now = LocalDateTime.now();

        Workspace workspace = new Workspace();
        workspace.setName(normalizeRequired(request.name()));
        workspace.setDescription(normalizeOptional(request.description()));
        workspace.setOwnerId(currentUser.getId());
        workspace.setCreatedBy(currentUser.getId());
        workspace.setStatus(WorkspaceStatus.ENABLED);
        workspaceMapper.insert(workspace);

        WorkspaceMember owner = new WorkspaceMember();
        owner.setWorkspaceId(workspace.getId());
        owner.setUserId(currentUser.getId());
        owner.setRole(WorkspaceRole.OWNER);
        owner.setJoinedAt(now);
        memberMapper.insert(owner);

        writeLog(workspace.getId(), currentUser.getId(), "CREATE_WORKSPACE", null,
                null, workspace.getName(), "创建工作空间");
        return WorkspaceVO.from(workspace, WorkspaceRole.OWNER);
    }

    /**
     * 查询当前用户加入的全部工作空间，按最近更新时间倒序返回。
     *
     * @return 当前用户可切换的工作空间
     */
    @Transactional(readOnly = true)
    public List<WorkspaceVO> listMine() {
        User currentUser = accessService.currentUser();
        List<WorkspaceMember> memberships = memberMapper.selectList(Wrappers.<WorkspaceMember>lambdaQuery()
                .eq(WorkspaceMember::getUserId, currentUser.getId()));
        if (memberships.isEmpty()) {
            return List.of();
        }

        Map<Long, WorkspaceRole> roles = memberships.stream().collect(Collectors.toMap(
                WorkspaceMember::getWorkspaceId, WorkspaceMember::getRole));
        return workspaceMapper.selectBatchIds(roles.keySet()).stream()
                .sorted(Comparator.comparing(Workspace::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(workspace -> WorkspaceVO.from(workspace, roles.get(workspace.getId())))
                .toList();
    }

    /**
     * 读取工作空间详情。前端选择该结果即可完成工作空间切换，服务端不保存隐式当前空间。
     *
     * @param workspaceId 工作空间主键
     * @return 工作空间详情和当前角色
     */
    @Transactional(readOnly = true)
    public WorkspaceVO get(Long workspaceId) {
        WorkspaceAccess access = accessService.requireReadable(workspaceId);
        return WorkspaceVO.from(access.workspace(), access.currentRole());
    }

    /**
     * 修改工作空间名称和描述，OWNER、ADMIN 或 SYSTEM_ADMIN 可执行。
     *
     * @param workspaceId 工作空间主键
     * @param request 修改参数
     * @return 修改后的工作空间
     */
    @Transactional
    public WorkspaceVO update(Long workspaceId, UpdateWorkspaceRequest request) {
        WorkspaceAccess access = accessService.requireMemberManagerForUpdate(workspaceId);
        Workspace workspace = access.workspace();
        String newName = normalizeRequired(request.name());
        String newDescription = normalizeOptional(request.description());

        if (!Objects.equals(workspace.getName(), newName)) {
            writeLog(workspaceId, access.currentUser().getId(), "UPDATE_WORKSPACE", "name",
                    workspace.getName(), newName, "修改工作空间名称");
            workspace.setName(newName);
        }
        if (!Objects.equals(workspace.getDescription(), newDescription)) {
            writeLog(workspaceId, access.currentUser().getId(), "UPDATE_WORKSPACE", "description",
                    workspace.getDescription(), newDescription, "修改工作空间描述");
            workspace.setDescription(newDescription);
        }
        workspaceMapper.updateById(workspace);
        return WorkspaceVO.from(workspace, access.currentRole());
    }

    /**
     * 查询工作空间成员列表，成员和 SYSTEM_ADMIN 均可读取。
     *
     * @param workspaceId 工作空间主键
     * @return 成员列表
     */
    @Transactional(readOnly = true)
    public List<WorkspaceMemberVO> listMembers(Long workspaceId) {
        accessService.requireReadable(workspaceId);
        List<WorkspaceMember> members = memberMapper.selectList(Wrappers.<WorkspaceMember>lambdaQuery()
                .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                .orderByAsc(WorkspaceMember::getId));
        Map<Long, User> users = userService.findByIds(members.stream()
                        .map(WorkspaceMember::getUserId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<WorkspaceMemberVO> result = new ArrayList<>(members.size());
        for (WorkspaceMember member : members) {
            User user = users.get(member.getUserId());
            // 成员关系理论上必须对应系统用户；若历史脏数据存在则跳过，避免整个列表不可用。
            if (user != null) {
                result.add(WorkspaceMemberVO.from(member, user));
            }
        }
        return result;
    }

    /**
     * 添加已存在且启用的系统用户，同一用户在一个工作空间只能加入一次。
     *
     * @param workspaceId 工作空间主键
     * @param request 成员信息
     * @return 新成员
     */
    @Transactional
    public WorkspaceMemberVO addMember(Long workspaceId, AddWorkspaceMemberRequest request) {
        WorkspaceAccess access = accessService.requireMemberManagerForUpdate(workspaceId);
        User targetUser = userService.findById(request.userId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, 40401, "用户不存在"));
        if (!Boolean.TRUE.equals(targetUser.getEnabled())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40305, "不能添加已禁用的用户");
        }
        if (findMember(workspaceId, request.userId()) != null) {
            throw duplicateMember();
        }

        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspaceId(workspaceId);
        member.setUserId(request.userId());
        member.setRole(request.role());
        member.setJoinedAt(LocalDateTime.now());
        try {
            memberMapper.insert(member);
        } catch (DuplicateKeyException exception) {
            // 数据库唯一索引是并发请求的最终防线，统一映射为稳定业务错误码。
            throw duplicateMember();
        }

        writeLog(workspaceId, access.currentUser().getId(), "ADD_MEMBER", "role",
                null, request.role().name(), "添加工作空间成员：" + targetUser.getUsername());
        return WorkspaceMemberVO.from(member, targetUser);
    }

    /**
     * 修改成员角色；降级最后一个 OWNER 时拒绝操作。
     *
     * @param workspaceId 工作空间主键
     * @param userId 目标用户主键
     * @param request 新角色
     * @return 修改后的成员
     */
    @Transactional
    public WorkspaceMemberVO updateMemberRole(
            Long workspaceId,
            Long userId,
            UpdateWorkspaceMemberRoleRequest request) {
        WorkspaceAccess access = accessService.requireMemberManagerForUpdate(workspaceId);
        WorkspaceMember member = requireTargetMember(workspaceId, userId);
        WorkspaceRole oldRole = member.getRole();
        if (oldRole == request.role()) {
            return WorkspaceMemberVO.from(member, requireUser(userId));
        }

        if (oldRole == WorkspaceRole.OWNER && request.role() != WorkspaceRole.OWNER) {
            ensureAnotherOwnerExists(workspaceId, userId);
        }
        member.setRole(request.role());
        memberMapper.updateById(member);

        // owner_id 表示主要负责人；主要负责人被降级时同步切换到仍保留的 OWNER。
        if (oldRole == WorkspaceRole.OWNER
                && request.role() != WorkspaceRole.OWNER
                && Objects.equals(access.workspace().getOwnerId(), userId)) {
            transferPrimaryOwner(access.workspace(), userId);
        }

        writeLog(workspaceId, access.currentUser().getId(), "CHANGE_MEMBER_ROLE", "role",
                oldRole.name(), request.role().name(), "修改成员角色，用户 ID：" + userId);
        return WorkspaceMemberVO.from(member, requireUser(userId));
    }

    /**
     * 移除成员。最后一个 OWNER 或仍承担未关闭 Bug 责任的成员不能被移除。
     *
     * @param workspaceId 工作空间主键
     * @param userId 目标用户主键
     */
    @Transactional
    public void removeMember(Long workspaceId, Long userId) {
        WorkspaceAccess access = accessService.requireMemberManagerForUpdate(workspaceId);
        WorkspaceMember member = requireTargetMember(workspaceId, userId);
        if (member.getRole() == WorkspaceRole.OWNER) {
            ensureAnotherOwnerExists(workspaceId, userId);
        }
        if (memberMapper.countOpenBugResponsibilities(workspaceId, userId) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, 40901,
                    "该成员仍是未关闭 Bug 的负责人或验收人，请先完成转派");
        }

        memberMapper.deleteById(member.getId());
        if (Objects.equals(access.workspace().getOwnerId(), userId)) {
            transferPrimaryOwner(access.workspace(), userId);
        }
        writeLog(workspaceId, access.currentUser().getId(), "REMOVE_MEMBER", "userId",
                userId.toString(), null, "移除工作空间成员");
    }

    /**
     * 停用工作空间但保留全部历史数据，只有 OWNER 或 SYSTEM_ADMIN 可执行。
     *
     * @param workspaceId 工作空间主键
     * @return 停用后的工作空间
     */
    @Transactional
    public WorkspaceVO disable(Long workspaceId) {
        WorkspaceAccess access = accessService.requireOwnerForUpdate(workspaceId);
        Workspace workspace = access.workspace();
        workspace.setStatus(WorkspaceStatus.DISABLED);
        workspaceMapper.updateById(workspace);
        writeLog(workspaceId, access.currentUser().getId(), "DISABLE_WORKSPACE", "status",
                WorkspaceStatus.ENABLED.name(), WorkspaceStatus.DISABLED.name(), "停用工作空间");
        return WorkspaceVO.from(workspace, access.currentRole());
    }

    /**
     * 重新启用已停用的工作空间，恢复成员变更和后续 Bug 写入能力。
     * 原成员及业务数据不做重建，状态切换和审计日志在同一事务中提交。
     *
     * @param workspaceId 工作空间主键
     * @return 重新启用后的工作空间
     */
    @Transactional
    public WorkspaceVO enable(Long workspaceId) {
        WorkspaceAccess access = accessService.requireOwnerForStatusChange(workspaceId);
        Workspace workspace = access.workspace();
        if (workspace.getStatus() == WorkspaceStatus.ENABLED) {
            throw new BusinessException(HttpStatus.CONFLICT, 40901, "工作空间已启用，无需重复启用");
        }

        workspace.setStatus(WorkspaceStatus.ENABLED);
        workspaceMapper.updateById(workspace);
        writeLog(workspaceId, access.currentUser().getId(), "ENABLE_WORKSPACE", "status",
                WorkspaceStatus.DISABLED.name(), WorkspaceStatus.ENABLED.name(), "重新启用工作空间");
        return WorkspaceVO.from(workspace, access.currentRole());
    }

    private WorkspaceMember findMember(Long workspaceId, Long userId) {
        return memberMapper.selectOne(Wrappers.<WorkspaceMember>lambdaQuery()
                .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                .eq(WorkspaceMember::getUserId, userId));
    }

    private WorkspaceMember requireTargetMember(Long workspaceId, Long userId) {
        WorkspaceMember member = findMember(workspaceId, userId);
        if (member == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40302, "目标用户不是当前工作空间成员");
        }
        return member;
    }

    private User requireUser(Long userId) {
        return userService.findById(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, 40401, "用户不存在"));
    }

    private void ensureAnotherOwnerExists(Long workspaceId, Long excludedUserId) {
        long remainingOwners = memberMapper.selectCount(Wrappers.<WorkspaceMember>lambdaQuery()
                .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                .eq(WorkspaceMember::getRole, WorkspaceRole.OWNER)
                .ne(WorkspaceMember::getUserId, excludedUserId));
        if (remainingOwners == 0) {
            throw new BusinessException(HttpStatus.CONFLICT, 40904, "不能删除或降级最后一个 Owner");
        }
    }

    private void transferPrimaryOwner(Workspace workspace, Long excludedUserId) {
        WorkspaceMember nextOwner = memberMapper.selectOne(Wrappers.<WorkspaceMember>lambdaQuery()
                .eq(WorkspaceMember::getWorkspaceId, workspace.getId())
                .eq(WorkspaceMember::getRole, WorkspaceRole.OWNER)
                .ne(WorkspaceMember::getUserId, excludedUserId)
                .orderByAsc(WorkspaceMember::getId)
                .last("LIMIT 1"));
        // 调用方已校验至少还有一个 OWNER；这里的保护用于识别意外脏数据或未遵循锁协议的写入。
        if (nextOwner == null) {
            throw new BusinessException(HttpStatus.CONFLICT, 40904, "工作空间必须至少保留一个 Owner");
        }
        workspace.setOwnerId(nextOwner.getUserId());
        workspaceMapper.updateById(workspace);
    }

    private void writeLog(
            Long workspaceId,
            Long operatorId,
            String operationType,
            String fieldName,
            String oldValue,
            String newValue,
            String description) {
        WorkspaceOperationLog log = new WorkspaceOperationLog();
        log.setWorkspaceId(workspaceId);
        log.setOperatorId(operatorId);
        log.setOperationType(operationType);
        log.setFieldName(fieldName);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setDescription(description);
        log.setCreatedAt(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    private String normalizeRequired(String value) {
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private BusinessException duplicateMember() {
        return new BusinessException(HttpStatus.CONFLICT, 40903, "用户已经是工作空间成员");
    }
}
