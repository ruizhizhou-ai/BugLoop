/**
 * 本文件在真实 MySQL 上验证注册、登录、当前用户与登出的完整链路。
 */
package com.wjfz.bugloop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证链路集成测试。
 */
class AuthFlowIT extends AbstractMysqlIntegrationTest {

    @BeforeEach
    void cleanUsers() {
        jdbcTemplate.update("DELETE FROM sys_user");
    }

    @Test
    void 注册登录登出链路应在真实数据上工作() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "mysql_user", "displayName": "真库用户", "password": "bugloop123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.systemRole").value("SYSTEM_ADMIN"));

        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "mysql_user", "password": "bugloop123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(loginBody, "$.data.token");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("mysql_user"));

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
