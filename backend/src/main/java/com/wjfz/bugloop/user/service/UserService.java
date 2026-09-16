/**
 * 本文件提供系统用户的基础读取与写入能力，供认证和后续业务模块复用。
 */
package com.wjfz.bugloop.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wjfz.bugloop.common.util.RandomIdGenerator;
import com.wjfz.bugloop.user.entity.User;
import com.wjfz.bugloop.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 系统用户服务。
 */
@Service
public class UserService {

    private static final int USER_OPTION_LIMIT = 20;

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 按主键查询用户。
     *
     * @param id 用户主键
     * @return 用户实体，不存在时为空
     */
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id));
    }

    /**
     * 按用户名精确查询用户。
     *
     * @param username 登录用户名
     * @return 用户实体，不存在时为空
     */
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username)));
    }

    /**
     * 批量查询用户，供成员列表等需要组合展示信息的业务使用。
     *
     * @param ids 用户主键集合
     * @return 已存在的用户列表；空集合直接返回空列表
     */
    public List<User> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return userMapper.selectBatchIds(ids);
    }

    /**
     * 查询全部系统用户，供系统管理员管理页面展示。
     *
     * @return 按创建时间倒序排列的用户列表
     */
    public List<User> findAll() {
        return userMapper.selectList(Wrappers.<User>lambdaQuery()
                .orderByDesc(User::getCreatedAt, User::getId));
    }

    /**
     * 查询可添加到指定工作空间的启用用户，支持按用户名或显示名称模糊匹配。
     *
     * @param workspaceId 工作空间主键
     * @param keyword 查询关键字，可为空
     * @return 最多 20 个尚未加入该工作空间的用户
     */
    public List<User> findEnabledUsersAvailableForWorkspace(Long workspaceId, String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        return userMapper.selectEnabledUsersAvailableForWorkspace(
                workspaceId, normalizedKeyword, USER_OPTION_LIMIT);
    }

    /**
     * 更新用户资料，当前用于启用 / 停用账号，调用方负责完成权限校验。
     *
     * @param user 待更新的用户实体
     * @return 更新后的用户
     */
    public User update(User user) {
        userMapper.updateById(user);
        return user;
    }

    /**
     * 新增用户，由调用方负责完成校验与密码哈希。
     *
     * @param user 待保存用户
     * @return 保存后的用户，包含应用生成的随机主键
     */
    public User save(User user) {
        // 统一在用户服务生成主键，避免认证或后续用户管理入口遗漏随机 ID 规则。
        if (user.getId() == null) {
            user.setId(RandomIdGenerator.nextId());
        }
        userMapper.insert(user);
        return user;
    }
}
