/**
 * 本文件覆盖注册、登录、鉴权和登出的主要成功与失败路径。
 */
package com.wjfz.bugloop.auth.controller;

import cn.dev33.satoken.secure.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jayway.jsonpath.JsonPath;
import com.wjfz.bugloop.common.id.RandomIdGenerator;
import com.wjfz.bugloop.user.entity.User;
import com.wjfz.bugloop.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证接口集成测试，使用 H2 内存库并执行与生产一致的 Flyway 迁移脚本。
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthControllerTest {

    private static final String REGISTER_BODY = """
            {"username": "zhangsan", "displayName": "张三", "password": "bugloop123"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @BeforeEach
    void cleanUsers() {
        userMapper.delete(Wrappers.<User>lambdaQuery().isNotNull(User::getId));
    }

    @Test
    void 注册成功时返回登录凭证与首要管理员角色() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value("zhangsan"))
                .andExpect(jsonPath("$.data.user.displayName").value("张三"))
                .andExpect(jsonPath("$.data.user.systemRole").value("SYSTEM_ADMIN"));
    }

    @Test
    void 第二个注册用户默认为普通角色() throws Exception {
        registerAndGetToken("zhangsan");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "lisi", "displayName": "李四", "password": "bugloop123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.systemRole").value("USER"));
    }

    @Test
    void 用户名重复时返回40905() throws Exception {
        registerAndGetToken("zhangsan");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905))
                .andExpect(jsonPath("$.message").value("用户名已存在"));
    }

    @Test
    void 密码不满足长度要求时返回40001() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "zhangsan", "displayName": "张三", "password": "1234567"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("密码长度需为 8-72 位"));
    }

    @Test
    void 用户名格式不合法时返回40001() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "张三", "displayName": "张三", "password": "bugloop123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void 登录成功后可使用凭证访问当前用户接口() throws Exception {
        String token = registerAndGetToken("zhangsan");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "zhangsan", "password": "bugloop123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("zhangsan"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void 密码错误时返回40102() throws Exception {
        registerAndGetToken("zhangsan");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "zhangsan", "password": "wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40102))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void 用户名不存在时返回40102且不泄露具体原因() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "nobody", "password": "bugloop123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40102))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void 禁用用户登录时返回40305() throws Exception {
        User disabledUser = new User();
        // 用户实体使用应用输入主键，直接写 Mapper 的测试也必须遵守同一生成规则。
        disabledUser.setId(RandomIdGenerator.nextId());
        disabledUser.setUsername("disabled");
        disabledUser.setDisplayName("已禁用");
        disabledUser.setPasswordHash(BCrypt.hashpw("bugloop123"));
        disabledUser.setSystemRole("USER");
        disabledUser.setEnabled(false);
        userMapper.insert(disabledUser);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "disabled", "password": "bugloop123"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40305))
                .andExpect(jsonPath("$.message").value("用户已被禁用"));
    }

    @Test
    void 未携带凭证访问受保护接口时返回40101() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void 登出后原凭证立即失效() throws Exception {
        String token = registerAndGetToken("zhangsan");

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    private String registerAndGetToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "displayName": "测试用户", "password": "bugloop123"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }
}
