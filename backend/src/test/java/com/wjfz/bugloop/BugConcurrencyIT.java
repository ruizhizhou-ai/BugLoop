/**
 * 本文件在真实 MySQL 上验证 Bug 写入的事务与锁行为：乐观版本冲突、
 * 状态流转互斥、验收竞争，以及描述历史与无变化更新的一致性。
 */
package com.wjfz.bugloop;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Bug 并发与事务集成测试。
 */
class BugConcurrencyIT extends AbstractMysqlIntegrationTest {

    private long ownerId;
    private long assigneeId;
    private long acceptorId;
    private long adminId;
    private String ownerToken;
    private String assigneeToken;
    private String acceptorToken;
    private String adminToken;
    private long workspaceId;

    @BeforeEach
    void seedWorkspace() throws Exception {
        cleanData();

        ownerId = register("owner");
        // 空间创建收紧为 SYSTEM_ADMIN / 既有 OWNER 后，测试用 owner 先取得平台管理员身份。
        jdbcTemplate.update("UPDATE sys_user SET system_role = 'SYSTEM_ADMIN' WHERE id = ?", ownerId);
        assigneeId = register("assignee");
        acceptorId = register("acceptor");
        adminId = register("space_admin");
        ownerToken = login("owner");
        assigneeToken = login("assignee");
        acceptorToken = login("acceptor");
        adminToken = login("space_admin");

        String body = mockMvc.perform(post("/api/workspaces")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"并发验证空间","description":null}
                                """))
                .andReturn()
                .getResponse()
                .getContentAsString();
        workspaceId = ((Number) JsonPath.read(body, "$.data.id")).longValue();

        addMember(ownerToken, assigneeId, "MEMBER");
        addMember(ownerToken, acceptorId, "MEMBER");
        addMember(ownerToken, adminId, "ADMIN");
    }

    @Test
    void 并发编辑同一版本时应只有一个请求成功() throws Exception {
        // 创建者与空间管理员都具备编辑权限，冲突只能来自版本条件。
        long bugId = createBug(ownerToken, "并发编辑", "初始描述", assigneeId, null);

        List<MvcResult> results = runConcurrently(
                () -> updateBug(ownerToken, bugId, "并发编辑", "甲修改的描述", 0),
                () -> updateBug(adminToken, bugId, "并发编辑", "乙修改的描述", 0));

        assertThat(statuses(results)).containsExactly(200, 409);

        MvcResult conflict = results.stream()
                .filter(result -> result.getResponse().getStatus() == 409)
                .findFirst()
                .orElseThrow();
        assertThat((Integer) JsonPath.read(conflict.getResponse().getContentAsString(), "$.code"))
                .isEqualTo(40902);

        assertThat(count("SELECT COUNT(*) FROM bug_description_history WHERE bug_id = " + bugId))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM bug_operation_log WHERE bug_id = " + bugId
                + " AND operation_type = 'UPDATE_DESCRIPTION'"))
                .isEqualTo(1);
        assertThat(bugVersion(ownerToken, bugId)).isEqualTo(1);
    }

    @Test
    void 重复的开始处理请求不能产生第二次状态流转() throws Exception {
        long bugId = createBug(ownerToken, "重复流转", "描述", assigneeId, null);

        List<MvcResult> results = runConcurrently(
                () -> mockMvc.perform(post("/api/bugs/{id}/start", bugId)
                        .header("Authorization", bearer(assigneeToken))).andReturn(),
                () -> mockMvc.perform(post("/api/bugs/{id}/start", bugId)
                        .header("Authorization", bearer(assigneeToken))).andReturn());

        assertThat(statuses(results)).containsExactly(200, 409);
        assertThat(count("SELECT COUNT(*) FROM bug WHERE id = " + bugId + " AND status = 'PROCESSING'"))
                .isEqualTo(1);
        // 状态只允许转换一次，审计日志不能记录两次 START_PROCESS。
        assertThat(count("SELECT COUNT(*) FROM bug_operation_log WHERE bug_id = " + bugId
                + " AND operation_type = 'START_PROCESS'")).isEqualTo(1);
    }

    @Test
    void 并发验收通过与驳回时只产生一条验收记录() throws Exception {
        long bugId = createBug(ownerToken, "并发验收", "描述", assigneeId, ownerId);

        mockMvc.perform(post("/api/bugs/{id}/start", bugId)
                .header("Authorization", bearer(assigneeToken)));
        mockMvc.perform(put("/api/bugs/{id}/fix-description", bugId)
                .header("Authorization", bearer(assigneeToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fixDescriptionMd":"已修复"}
                        """));
        mockMvc.perform(post("/api/bugs/{id}/submit", bugId)
                .header("Authorization", bearer(assigneeToken)));

        List<MvcResult> results = runConcurrently(
                () -> mockMvc.perform(post("/api/bugs/{id}/accept", bugId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"commentMd":"通过"}
                                """)).andReturn(),
                () -> mockMvc.perform(post("/api/bugs/{id}/reject", bugId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"commentMd":"需要返工"}
                                """)).andReturn());

        assertThat(statuses(results)).containsExactly(200, 409);

        assertThat(count("SELECT COUNT(*) FROM bug_acceptance WHERE bug_id = " + bugId)).isEqualTo(1);
        String status = jdbcTemplate.queryForObject("SELECT status FROM bug WHERE id = " + bugId, String.class);
        String result = jdbcTemplate.queryForObject(
                "SELECT result FROM bug_acceptance WHERE bug_id = " + bugId, String.class);
        // 业务状态、验收结果与 reopen_count 必须来自同一次成功事务，不能出现交叉写入。
        assertThat(status).isIn("CLOSED", "REOPENED");
        assertThat(result).isEqualTo("CLOSED".equals(status) ? "PASS" : "REJECT");
        int expectedReopenCount = "REOPENED".equals(status) ? 1 : 0;
        assertThat(count("SELECT COUNT(*) FROM bug WHERE id = " + bugId
                + " AND reopen_count = " + expectedReopenCount)).isEqualTo(1);
    }

    @Test
    void 描述修改应保留历史且内容未变化时不新增版本() throws Exception {
        long bugId = createBug(ownerToken, "描述历史", "第一版描述", null, null);

        updateBug(ownerToken, bugId, "描述历史", "第二版描述", 0);
        assertThat(count("SELECT COUNT(*) FROM bug_description_history WHERE bug_id = " + bugId))
                .isEqualTo(1);
        String archived = jdbcTemplate.queryForObject(
                "SELECT content_md FROM bug_description_history WHERE bug_id = " + bugId, String.class);
        assertThat(archived).isEqualTo("第一版描述");

        updateBug(ownerToken, bugId, "描述历史", "第二版描述", 1);
        assertThat(count("SELECT COUNT(*) FROM bug_description_history WHERE bug_id = " + bugId))
                .isEqualTo(1);
        assertThat(bugVersion(ownerToken, bugId)).isEqualTo(1);
    }

    private List<MvcResult> runConcurrently(Callable<MvcResult> first, Callable<MvcResult> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            Future<MvcResult> firstFuture = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return first.call();
            });
            Future<MvcResult> secondFuture = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return second.call();
            });
            return List.of(firstFuture.get(30, TimeUnit.SECONDS), secondFuture.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private List<Integer> statuses(List<MvcResult> results) {
        return results.stream().map(result -> result.getResponse().getStatus()).sorted().toList();
    }

    private long createBug(String token, String title, String description, Long assigneeId, Long acceptorId) throws Exception {
        String body = mockMvc.perform(post("/api/workspaces/{id}/bugs", workspaceId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","descriptionMd":"%s","priority":"P2","assigneeId":%s,"acceptorId":%s}
                                """.formatted(title, description, jsonId(assigneeId), jsonId(acceptorId))))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private MvcResult updateBug(String token, long bugId, String title, String description, int version) throws Exception {
        return mockMvc.perform(put("/api/bugs/{id}", bugId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"%s","descriptionMd":"%s","priority":"P2","version":%d}
                        """.formatted(title, description, version)))
                .andReturn();
    }

    private int bugVersion(String token, long bugId) throws Exception {
        String body = mockMvc.perform(get("/api/bugs/{id}", bugId)
                        .header("Authorization", bearer(token)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return (Integer) JsonPath.read(body, "$.data.version");
    }

    private long register(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","displayName":"%s","password":"bugloop123"}
                                """.formatted(username, username)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.user.id")).longValue();
    }

    private String login(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"bugloop123"}
                                """.formatted(username)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.data.token");
    }

    private void addMember(String token, long userId, String role) throws Exception {
        mockMvc.perform(post("/api/workspaces/{id}/members", workspaceId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"role":"%s"}
                                """.formatted(userId, role)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));
    }

    private String jsonId(Long id) {
        return id == null ? "null" : id.toString();
    }

    private long count(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private void cleanData() {
        for (String table : List.of("bug_acceptance", "bug_description_history", "bug_operation_log",
                "bug_comment", "bug_attachment", "bug", "workspace_operation_log",
                "workspace_member", "workspace", "sys_user")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }
}
