/**
 * 本文件验证系统管理员创建和查询用户的接口权限、密码安全及会话隔离。
 */
package com.wjfz.bugloop.user.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 系统用户管理接口集成测试，所有操作通过真实认证会话执行。
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanData() {
        jdbcTemplate.update("DELETE FROM workspace_member");
        jdbcTemplate.update("DELETE FROM workspace");
        jdbcTemplate.update("DELETE FROM sys_user");
    }

    @Test
    void 系统管理员创建普通用户且保持当前会话() throws Exception {
        Session systemAdmin = registerSystemAdmin("system_admin");

        mockMvc.perform(post("/api/users")
                        .header("Authorization", bearer(systemAdmin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"new_user","displayName":"新用户","password":"bugloop123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("new_user"))
                .andExpect(jsonPath("$.data.systemRole").value("USER"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        // 管理员创建账号不应像公开注册一样切换当前会话。
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", bearer(systemAdmin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(systemAdmin.userId()));

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM sys_user WHERE username = 'new_user'", String.class);
        assertThat(passwordHash).isNotEqualTo("bugloop123").startsWith("$2");
    }

    @Test
    void 普通用户不能查询或创建系统用户() throws Exception {
        Session ordinaryUser = register("ordinary_user");

        mockMvc.perform(get("/api/users")
                        .header("Authorization", bearer(ordinaryUser.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(post("/api/users")
                        .header("Authorization", bearer(ordinaryUser.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"blocked_user","displayName":"无权限","password":"bugloop123"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void 用户列表和创建参数应遵循统一契约() throws Exception {
        Session systemAdmin = registerSystemAdmin("system_admin");
        register("existing_user");

        mockMvc.perform(get("/api/users")
                        .header("Authorization", bearer(systemAdmin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist());

        mockMvc.perform(post("/api/users")
                        .header("Authorization", bearer(systemAdmin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"existing_user","displayName":"重复用户","password":"bugloop123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));

        mockMvc.perform(post("/api/users")
                        .header("Authorization", bearer(systemAdmin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"x","displayName":"参数错误","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void 停用普通用户后禁止登录且在线会话立即失效() throws Exception {
        Session systemAdmin = registerSystemAdmin("system_admin");
        Session target = register("target_user");

        mockMvc.perform(post("/api/users/{id}/disable", target.userId())
                        .header("Authorization", bearer(systemAdmin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        // 停用必须同时终止在用会话，否则旧 Token 仍能继续访问。
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(target.token())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        login("target_user")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40305));

        mockMvc.perform(post("/api/users/{id}/enable", target.userId())
                        .header("Authorization", bearer(systemAdmin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        // 重新启用后原密码即可登录。
        login("target_user").andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT enabled FROM sys_user WHERE username = 'target_user'", Boolean.class)).isTrue();
    }

    @Test
    void 停用启用应遵守权限与状态边界() throws Exception {
        Session systemAdmin = registerSystemAdmin("system_admin");
        Session ordinaryUser = register("ordinary_user");
        Session target = register("target_user");

        mockMvc.perform(post("/api/users/{id}/disable", target.userId())
                        .header("Authorization", bearer(ordinaryUser.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        // SYSTEM_ADMIN 账号（包括自己）不在停用范围内。
        mockMvc.perform(post("/api/users/{id}/disable", systemAdmin.userId())
                        .header("Authorization", bearer(systemAdmin.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(post("/api/users/{id}/disable", 999999999999L)
                        .header("Authorization", bearer(systemAdmin.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        mockMvc.perform(post("/api/users/{id}/disable", target.userId())
                        .header("Authorization", bearer(systemAdmin.token())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/users/{id}/disable", target.userId())
                        .header("Authorization", bearer(systemAdmin.token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));

        mockMvc.perform(post("/api/users/{id}/enable", target.userId())
                        .header("Authorization", bearer(systemAdmin.token())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/users/{id}/enable", target.userId())
                        .header("Authorization", bearer(systemAdmin.token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    /** 注册账号并提升为 SYSTEM_ADMIN；管理员由内置初始化提供，不再由首个注册用户自动获得。 */
    private Session registerSystemAdmin(String username) throws Exception {
        Session session = register(username);
        jdbcTemplate.update("UPDATE sys_user SET system_role = 'SYSTEM_ADMIN' WHERE id = ?", session.userId());
        return session;
    }

    /** 以初始密码登录指定账号，用于验证停用与启用对登录能力的影响。 */
    private org.springframework.test.web.servlet.ResultActions login(String username) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"bugloop123"}
                        """.formatted(username)));
    }

    /** 注册测试账号并保留用户主键和真实 Token。 */
    private Session register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","displayName":"%s","password":"bugloop123"}
                                """.formatted(username, username)))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        Number userId = JsonPath.read(body, "$.data.user.id");
        String token = JsonPath.read(body, "$.data.token");
        return new Session(userId.longValue(), token);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    /** 测试会话保存账号主键和登录凭证，便于断言管理员会话未被替换。 */
    private record Session(long userId, String token) {
    }
}
