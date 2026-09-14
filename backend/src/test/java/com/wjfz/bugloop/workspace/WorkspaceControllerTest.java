/**
 * 本文件使用 H2 覆盖工作空间创建、切换、成员角色、隔离、停用和关键冲突规则。
 */
package com.wjfz.bugloop.workspace;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Workspace API 集成测试，验证 Milestone 2 的主要验收场景。
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class WorkspaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanData() {
        // 表没有物理外键，但仍按业务依赖从下到上清理，便于未来增加约束时保持测试稳定。
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
    void 创建后应成为Owner并能在列表和详情中切换() throws Exception {
        register("system_admin");
        Session owner = register("owner");

        long workspaceId = createWorkspace(owner.token(), " 研发中心 ");

        mockMvc.perform(get("/api/workspaces").header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(workspaceId))
                .andExpect(jsonPath("$.data[0].name").value("研发中心"))
                .andExpect(jsonPath("$.data[0].currentUserRole").value("OWNER"));

        mockMvc.perform(get("/api/workspaces/{id}", workspaceId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentUserRole").value("OWNER"));

        mockMvc.perform(put("/api/workspaces/{id}", workspaceId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"研发一部","description":"核心研发团队"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("研发一部"))
                .andExpect(jsonPath("$.data.description").value("核心研发团队"));

        Integer logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workspace_operation_log WHERE workspace_id = ?", Integer.class, workspaceId);
        assertThat(logCount).isEqualTo(3);
    }

    @Test
    void 成员管理应校验重复关系和角色权限() throws Exception {
        register("system_admin");
        Session owner = register("owner");
        Session member = register("member");
        Session another = register("another");
        long workspaceId = createWorkspace(owner.token(), "研发中心");

        addMember(owner.token(), workspaceId, member.userId(), "MEMBER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MEMBER"));

        addMember(owner.token(), workspaceId, member.userId(), "MEMBER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903));

        addMember(member.token(), workspaceId, another.userId(), "MEMBER")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(put("/api/workspaces/{workspaceId}/members/{userId}/role",
                                workspaceId, member.userId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        addMember(member.token(), workspaceId, another.userId(), "MEMBER")
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/workspaces/{id}", workspaceId)
                        .header("Authorization", bearer(member.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"管理员可编辑\",\"description\":\"由 ADMIN 更新\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("管理员可编辑"));

        mockMvc.perform(get("/api/workspaces/{id}/members", workspaceId)
                        .header("Authorization", bearer(another.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    void 非成员不能访问但SystemAdmin可以处理异常空间() throws Exception {
        Session systemAdmin = register("system_admin");
        Session owner = register("owner");
        Session outsider = register("outsider");
        long workspaceId = createWorkspace(owner.token(), "隔离空间");

        mockMvc.perform(get("/api/workspaces/{id}", workspaceId)
                        .header("Authorization", bearer(outsider.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40302));

        mockMvc.perform(get("/api/workspaces/{id}", workspaceId)
                        .header("Authorization", bearer(systemAdmin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentUserRole").doesNotExist());
    }

    @Test
    void 最后一个Owner不能降级且主要负责人应随Owner转移() throws Exception {
        register("system_admin");
        Session owner = register("owner");
        Session nextOwner = register("next_owner");
        long workspaceId = createWorkspace(owner.token(), "Owner 规则");

        updateRole(owner.token(), workspaceId, owner.userId(), "MEMBER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40904));

        addMember(owner.token(), workspaceId, nextOwner.userId(), "OWNER")
                .andExpect(status().isOk());
        updateRole(owner.token(), workspaceId, owner.userId(), "MEMBER")
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workspaces/{id}", workspaceId)
                        .header("Authorization", bearer(nextOwner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerId").value(nextOwner.userId()));
    }

    @Test
    void 停用后应保留读取能力并拒绝全部成员变更() throws Exception {
        register("system_admin");
        Session owner = register("owner");
        Session member = register("member");
        long workspaceId = createWorkspace(owner.token(), "待停用空间");

        mockMvc.perform(post("/api/workspaces/{id}/disable", workspaceId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(get("/api/workspaces/{id}", workspaceId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        addMember(owner.token(), workspaceId, member.userId(), "MEMBER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));

        mockMvc.perform(put("/api/workspaces/{id}", workspaceId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"不可修改\",\"description\":null}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    void 承担未关闭Bug责任的成员不能移除() throws Exception {
        register("system_admin");
        Session owner = register("owner");
        Session member = register("member");
        long workspaceId = createWorkspace(owner.token(), "Bug 责任空间");
        addMember(owner.token(), workspaceId, member.userId(), "MEMBER")
                .andExpect(status().isOk());

        jdbcTemplate.update("""
                INSERT INTO bug (
                    workspace_id, bug_no, title, description_md, priority, status,
                    creator_id, assignee_id, acceptor_id, reopen_count, version, created_at, updated_at)
                VALUES (?, 'BUG-000001', '测试 Bug', '描述', 'P2', 'TODO', ?, ?, ?, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, workspaceId, owner.userId(), member.userId(), owner.userId());

        mockMvc.perform(delete("/api/workspaces/{workspaceId}/members/{userId}",
                                workspaceId, member.userId())
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    void 非法角色应返回参数错误而不是系统异常() throws Exception {
        register("system_admin");
        Session owner = register("owner");
        Session member = register("member");
        long workspaceId = createWorkspace(owner.token(), "参数校验空间");

        addMember(owner.token(), workspaceId, member.userId(), "SUPER_ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
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

    private long createWorkspace(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/workspaces")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"测试空间"}
                                """.formatted(name)))
                .andExpect(status().isOk())
                .andReturn();
        Number workspaceId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return workspaceId.longValue();
    }

    private org.springframework.test.web.servlet.ResultActions addMember(
            String token, long workspaceId, long userId, String role) throws Exception {
        return mockMvc.perform(post("/api/workspaces/{id}/members", workspaceId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"userId":%d,"role":"%s"}
                        """.formatted(userId, role)));
    }

    private org.springframework.test.web.servlet.ResultActions updateRole(
            String token, long workspaceId, long userId, String role) throws Exception {
        return mockMvc.perform(put("/api/workspaces/{workspaceId}/members/{userId}/role", workspaceId, userId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"%s\"}".formatted(role)));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    /**
     * 测试会话同时保存用户主键和 Token，便于构造跨用户权限场景。
     *
     * @param userId 用户主键
     * @param token 登录凭证
     */
    private record Session(long userId, String token) {
    }
}
