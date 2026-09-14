/**
 * 本文件在真实 MySQL 上验证工作空间创建、成员加入、角色变更和启停的完整链路。
 */
package com.wjfz.bugloop;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 工作空间真实数据库集成测试，Docker 不可用时由基类自动跳过。
 */
class WorkspaceFlowIT extends AbstractMysqlIntegrationTest {

    @BeforeEach
    void cleanData() {
        // 真实库用例按依赖顺序清理，保证重复执行时不受上一次自增数据和成员关系影响。
        jdbcTemplate.update("DELETE FROM bug_acceptance");
        jdbcTemplate.update("DELETE FROM bug_description_history");
        jdbcTemplate.update("DELETE FROM bug_operation_log");
        jdbcTemplate.update("DELETE FROM bug_comment");
        jdbcTemplate.update("DELETE FROM bug_attachment");
        jdbcTemplate.update("DELETE FROM bug");
        jdbcTemplate.update("DELETE FROM workspace_operation_log");
        jdbcTemplate.update("DELETE FROM workspace_member");
        jdbcTemplate.update("DELETE FROM workspace");
        jdbcTemplate.update("DELETE FROM sys_user");
    }

    @Test
    void 工作空间主链路应在真实MySQL上保持事务一致() throws Exception {
        register("system_admin");
        Session owner = register("mysql_owner");
        Session member = register("mysql_member");

        MvcResult createResult = mockMvc.perform(post("/api/workspaces")
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"MySQL 研发空间","description":"真实数据库验证"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentUserRole").value("OWNER"))
                .andReturn();
        Number workspaceIdValue = JsonPath.read(
                createResult.getResponse().getContentAsString(), "$.data.id");
        long workspaceId = workspaceIdValue.longValue();

        mockMvc.perform(post("/api/workspaces/{id}/members", workspaceId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"role":"MEMBER"}
                                """.formatted(member.userId())))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/workspaces/{workspaceId}/members/{userId}/role",
                                workspaceId, member.userId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        mockMvc.perform(get("/api/workspaces/{id}", workspaceId)
                        .header("Authorization", bearer(member.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentUserRole").value("ADMIN"));

        mockMvc.perform(post("/api/workspaces/{id}/disable", workspaceId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(post("/api/workspaces/{id}/enable", workspaceId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENABLED"));

        Integer memberCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workspace_member WHERE workspace_id = ?", Integer.class, workspaceId);
        Integer logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workspace_operation_log WHERE workspace_id = ?", Integer.class, workspaceId);
        assertThat(memberCount).isEqualTo(2);
        assertThat(logCount).isEqualTo(5);
    }

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

    /**
     * 保存真实数据库测试所需的用户主键和登录凭证。
     *
     * @param userId 用户主键
     * @param token 登录凭证
     */
    private record Session(long userId, String token) {
    }
}
