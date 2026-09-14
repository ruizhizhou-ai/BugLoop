/**
 * 本文件提供系统用户的基础读取与写入能力，供认证和后续业务模块复用。
 */
package com.wjfz.bugloop.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 系统用户服务。
 */
@Service
public class UserService {

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
     * 统计系统用户总数，用于判断是否需要引导首个管理员。
     *
     * @return 用户总数
     */
    public long countUsers() {
        return userMapper.selectCount(null);
    }

    /**
     * 新增用户，由调用方负责完成校验与密码哈希。
     *
     * @param user 待保存用户
     * @return 保存后的用户，包含自增主键
     */
    public User save(User user) {
        userMapper.insert(user);
        return user;
    }
}
