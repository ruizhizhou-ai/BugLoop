/**
 * 本文件验证 Bug API 的默认值、空间隔离、字段白名单、状态机、并发及事务回滚。
 * 基础用例使用 H2，同一测试集由 BugApiMysqlIT 复用到真实 MySQL，避免只验证兼容数据库。
 */
package com.wjfz.bugloop.bug.controller;

import com.jayway.jsonpath.JsonPath;
import com.wjfz.bugloop.bug.entity.Bug;
import com.wjfz.bugloop.bug.mapper.BugAuditMapper;
import com.wjfz.bugloop.bug.mapper.BugMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Bug API 端到端契约测试，操作均通过真实登录会话和 HTTP 控制器执行。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class BugControllerTest {
    @Autowired protected MockMvc mockMvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected BugMapper bugs;
    @MockitoSpyBean protected BugAuditMapper audit;
    private Session systemAdmin;
    private Session owner;
    private Session developer;
    private Session tester;
    private Session outsider;
    private long workspaceId;

    /** 建立空间与三个不同职责的账号，每个用例从独立业务数据开始。 */
    @BeforeEach
    protected void prepare() throws Exception {
        for (String table : List.of("bug_acceptance", "bug_description_history", "bug_operation_log",
                "bug_comment", "bug_attachment", "bug", "workspace_operation_log", "workspace_member",
                "workspace", "sys_user")) {
            jdbc.update("DELETE FROM " + table);
        }
        systemAdmin = register("sysadmin");
        owner = register("owner");
        developer = register("developer");
        tester = register("tester");
        outsider = register("outsider");
        workspaceId = number(ok(owner, post("/api/workspaces")
                .content("{\"name\":\"研发空间\"}")), "$.data.id");
        addMember(developer, "MEMBER");
        addMember(tester, "MEMBER");
    }

    /** 创建默认值和公开编号必须稳定，跨空间编号也不能重复。 */
    @Test
    protected void 创建默认值编号与详情() throws Exception {
        long bugId = create(owner, "{\"title\":\" 默认 Bug \",\"descriptionMd\":\"# 描述\"}");
        ok(owner, get("/api/bugs/{id}", bugId))
                .andExpect(jsonPath("$.data.title").value("默认 Bug"))
                .andExpect(jsonPath("$.data.bugNo").value("BUG-%06d".formatted(bugId)))
                .andExpect(jsonPath("$.data.status").value("TODO"))
                .andExpect(jsonPath("$.data.priority").value("P2"))
                .andExpect(jsonPath("$.data.assigneeId").doesNotExist())
                .andExpect(jsonPath("$.data.acceptorId").value(owner.id()))
                .andExpect(jsonPath("$.data.creator.displayName").value("owner"))
                .andExpect(jsonPath("$.data.workspace.id").value(workspaceId))
                .andExpect(jsonPath("$.data.attachments").isEmpty())
                .andExpect(jsonPath("$.data.latestAcceptance").doesNotExist())
                .andExpect(jsonPath("$.data.creator.passwordHash").doesNotExist());
        long otherSpace = number(ok(outsider, post("/api/workspaces").content("{\"name\":\"外部空间\"}")), "$.data.id");
        long otherBug = number(ok(outsider, post("/api/workspaces/{id}/bugs", otherSpace)
                .content("{\"title\":\"另一问题\",\"descriptionMd\":\"描述\"}")), "$.data.id");
        assertThat(otherBug).isNotEqualTo(bugId);
        assertThat(count("bug_operation_log", bugId)).isEqualTo(1);
    }

    /** 分页不能把其他空间或不匹配条件的问题混入，列表不携带大段 Markdown。 */
    @Test
    protected void 列表分页筛选和空间隔离() throws Exception {
        long first = createAssigned(owner, "筛选目标");
        create(owner, "{\"title\":\"不匹配\",\"descriptionMd\":\"描述\",\"priority\":\"P3\"}");
        ok(outsider, post("/api/workspaces").content("{\"name\":\"另一个空间\"}"));
        ResultActions result = ok(owner, get("/api/workspaces/{id}/bugs", workspaceId)
                .param("page", "1").param("pageSize", "1").param("keyword", "筛选")
                .param("status", "TODO").param("priority", "P2")
                .param("creatorId", owner.id().toString()).param("assigneeId", developer.id().toString())
                .param("acceptorId", tester.id().toString())
                .param("startDate", LocalDate.now().toString()).param("endDate", LocalDate.now().toString()));
        result.andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(first))
                .andExpect(jsonPath("$.data.records[0].assignee.username").value("developer"))
                .andExpect(jsonPath("$.data.records[0].descriptionMd").doesNotExist())
                .andExpect(jsonPath("$.data.pageSize").value(1));
        ok(owner, get("/api/workspaces/{id}/bugs", workspaceId).param("page", "2").param("pageSize", "1")
                .param("keyword", "筛选")).andExpect(jsonPath("$.data.records").isEmpty())
                .andExpect(jsonPath("$.data.total").value(1));
        ok(owner, get("/api/workspaces/{id}/bugs", workspaceId).param("keyword", "BUG-%06d".formatted(first)))
                .andExpect(jsonPath("$.data.total").value(1));
        fail(outsider, get("/api/workspaces/{id}/bugs", workspaceId), 403, 40302);
        fail(outsider, get("/api/bugs/{id}", first), 403, 40302);
        fail(outsider, post("/api/bugs/{id}/start", first), 403, 40302);
        ok(systemAdmin, get("/api/bugs/{id}", first));
    }

    /** 所有角色创建时都必须是实际成员，责任人只能选择有效的当前空间成员。 */
    @Test
    protected void 创建和人员指派的成员边界() throws Exception {
        fail(systemAdmin, post("/api/workspaces/{id}/bugs", workspaceId)
                .content("{\"title\":\"越界\",\"descriptionMd\":\"描述\"}"), 400, 42205);
        fail(owner, post("/api/workspaces/{id}/bugs", workspaceId).content(
                "{\"title\":\"越界\",\"descriptionMd\":\"描述\",\"assigneeId\":%d}".formatted(outsider.id())), 400, 42205);
        long id = createAssigned(owner, "指派");
        fail(developer, post("/api/bugs/{id}/assign", id).content("{\"assigneeId\":%d}".formatted(tester.id())), 403, 40301);
        fail(owner, post("/api/bugs/{id}/assign", id).content("{\"assigneeId\":%d}".formatted(outsider.id())), 400, 42205);
        fail(owner, post("/api/bugs/{id}/acceptor", id).content("{\"acceptorId\":%d}".formatted(outsider.id())), 400, 42205);
        jdbc.update("UPDATE sys_user SET enabled = 0 WHERE id = ?", tester.id());
        fail(owner, post("/api/bugs/{id}/assign", id).content("{\"assigneeId\":%d}".formatted(tester.id())), 403, 40305);
        jdbc.update("UPDATE sys_user SET enabled = 1 WHERE id = ?", tester.id());
        ok(owner, post("/api/bugs/{id}/assign", id).content("{\"assigneeId\":%d}".formatted(tester.id())))
                .andExpect(jsonPath("$.data.assigneeId").value(tester.id()));
        ok(owner, post("/api/bugs/{id}/acceptor", id).content("{\"acceptorId\":%d}".formatted(developer.id())))
                .andExpect(jsonPath("$.data.acceptorId").value(developer.id()));
    }

    /** 描述修改保存旧版本，无变化不生成历史；禁止以普通更新覆盖状态或责任字段。 */
    @Test
    protected void 编辑权限字段白名单和描述历史() throws Exception {
        long id = createAssigned(developer, "原始标题");
        fail(tester, put("/api/bugs/{id}", id).content(updateBody(0, "新描述")), 403, 40301);
        for (String field : List.of("status", "workspaceId", "creatorId", "assigneeId", "acceptorId", "closedAt")) {
            String body = updateBody(0, "新描述");
            body = body.substring(0, body.length() - 1) + ",\"" + field + "\":\"CLOSED\"}";
            fail(developer, put("/api/bugs/{id}", id).content(body), 400, 40001);
        }
        ok(developer, put("/api/bugs/{id}", id).content(updateBody(0, "新描述")))
                .andExpect(jsonPath("$.data.version").value(1));
        assertThat(count("bug_description_history", id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT content_md FROM bug_description_history WHERE bug_id = ?",
                String.class, id)).isEqualTo("# 原始描述");
        int logs = count("bug_operation_log", id);
        ok(developer, put("/api/bugs/{id}", id).content(updateBody(1, "新描述")))
                .andExpect(jsonPath("$.data.version").value(1));
        assertThat(count("bug_operation_log", id)).isEqualTo(logs);
        assertThat(count("bug_description_history", id)).isEqualTo(1);
        fail(owner, put("/api/bugs/{id}", id).content(updateBody(0, "过期版本")), 409, 40902);
        ok(owner, put("/api/bugs/{id}", id).content(updateBody(1, "再次修改")));
        assertThat(jdbc.queryForObject("SELECT MAX(version_no) FROM bug_description_history WHERE bug_id = ?",
                Integer.class, id)).isEqualTo(2);
    }

    /** 修复、驳回、重新处理、通过的完整闭环要保留每次验收，并冻结关闭状态。 */
    @Test
    protected void 状态闭环驳回重做与关闭终态() throws Exception {
        long id = createAssigned(owner, "完整流程");
        fail(owner, post("/api/bugs/{id}/start", id), 403, 40303);
        fail(systemAdmin, post("/api/bugs/{id}/start", id), 403, 40303);
        fail(tester, post("/api/bugs/{id}/accept", id), 409, 40901);
        fail(developer, post("/api/bugs/{id}/submit", id), 409, 40901);
        fail(developer, put("/api/bugs/{id}/fix-description", id).content("{\"fixDescriptionMd\":\"过早\"}"), 409, 40901);
        ok(developer, post("/api/bugs/{id}/start", id)).andExpect(jsonPath("$.data.status").value("PROCESSING"));
        fail(developer, post("/api/bugs/{id}/submit", id), 400, 42201);
        fix(developer, id, "修复内容");
        ok(developer, post("/api/bugs/{id}/submit", id)).andExpect(jsonPath("$.data.status").value("WAIT_ACCEPTANCE"));
        fail(developer, post("/api/bugs/{id}/accept", id), 403, 40304);
        fail(systemAdmin, post("/api/bugs/{id}/accept", id), 403, 40304);
        fail(owner, post("/api/bugs/{id}/assign", id).content("{\"assigneeId\":%d}".formatted(tester.id())), 409, 40901);
        fail(owner, post("/api/bugs/{id}/acceptor", id).content("{\"acceptorId\":%d}".formatted(owner.id())), 409, 40901);
        fail(tester, post("/api/bugs/{id}/reject", id).content("{\"commentMd\":\"   \"}"), 400, 42202);
        assertThat(count("bug_acceptance", id)).isZero();
        ok(tester, post("/api/bugs/{id}/reject", id).content("{\"commentMd\":\"边界场景仍然失败\"}"))
                .andExpect(jsonPath("$.data.status").value("REOPENED"))
                .andExpect(jsonPath("$.data.reopenCount").value(1))
                .andExpect(jsonPath("$.data.closedAt").doesNotExist())
                .andExpect(jsonPath("$.data.latestAcceptance.result").value("REJECT"));
        fail(tester, post("/api/bugs/{id}/reject", id).content("{\"commentMd\":\"重复驳回\"}"), 409, 40901);
        fix(developer, id, "补充边界处理");
        fail(developer, post("/api/bugs/{id}/submit", id), 409, 40901);
        ok(developer, post("/api/bugs/{id}/start", id));
        ok(developer, post("/api/bugs/{id}/submit", id));
        ResultActions accepted = ok(tester, post("/api/bugs/{id}/accept", id).content("{\"commentMd\":\"验证通过\"}"));
        accepted.andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.closedAt").exists())
                .andExpect(jsonPath("$.data.latestAcceptance.result").value("PASS"))
                .andExpect(jsonPath("$.data.latestAcceptance.acceptorId").value(tester.id()));
        int version = (int) number(accepted, "$.data.version");
        fail(tester, post("/api/bugs/{id}/accept", id), 409, 40901);
        fail(tester, post("/api/bugs/{id}/reject", id).content("{\"commentMd\":\"重开\"}"), 409, 40901);
        fail(owner, put("/api/bugs/{id}", id).content(updateBody(version, "修改终态")), 409, 40901);
        fail(developer, put("/api/bugs/{id}/fix-description", id).content("{\"fixDescriptionMd\":\"修改终态\"}"), 409, 40901);
        fail(developer, post("/api/bugs/{id}/start", id), 409, 40901);
        assertThat(count("bug_acceptance", id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bug_operation_log WHERE bug_id = ? AND operation_type = 'ACCEPT_BUG'",
                Integer.class, id)).isEqualTo(1);
    }

    /** 停用空间只能读取；重新启用后恢复处理能力。 */
    @Test
    protected void 停用空间拒绝全部写操作() throws Exception {
        long id = createAssigned(owner, "停用隔离");
        ok(owner, post("/api/workspaces/{id}/disable", workspaceId));
        ok(owner, get("/api/bugs/{id}", id));
        ok(owner, get("/api/workspaces/{id}/bugs", workspaceId));
        fail(owner, post("/api/workspaces/{id}/bugs", workspaceId).content("{\"title\":\"问题\",\"descriptionMd\":\"描述\"}"), 409, 40901);
        fail(owner, put("/api/bugs/{id}", id).content(updateBody(0, "停用")), 409, 40901);
        fail(developer, post("/api/bugs/{id}/start", id), 409, 40901);
        fail(owner, post("/api/bugs/{id}/assign", id).content("{\"assigneeId\":%d}".formatted(tester.id())), 409, 40901);
        fail(owner, post("/api/bugs/{id}/acceptor", id).content("{\"acceptorId\":%d}".formatted(owner.id())), 409, 40901);
        fail(developer, put("/api/bugs/{id}/fix-description", id).content("{\"fixDescriptionMd\":\"修复\"}"), 409, 40901);
        fail(developer, post("/api/bugs/{id}/submit", id), 409, 40901);
        fail(tester, post("/api/bugs/{id}/accept", id), 409, 40901);
        fail(tester, post("/api/bugs/{id}/reject", id).content("{\"commentMd\":\"原因\"}"), 409, 40901);
        assertThat(count("bug_operation_log", id)).isEqualTo(1);
        ok(owner, post("/api/workspaces/{id}/enable", workspaceId));
        ok(developer, post("/api/bugs/{id}/start", id));
    }

    /** 输入错误必须是用户可理解的 400，认证、缺失 Bug 分别采用 401 和 404。 */
    @Test
    protected void 参数认证和缺失记录() throws Exception {
        mockMvc.perform(get("/api/workspaces/{id}/bugs", workspaceId)).andExpect(status().isUnauthorized());
        fail(owner, get("/api/bugs/9223372036854775807"), 404, 40403);
        fail(owner, get("/api/bugs/not-a-number"), 400, 40001);
        for (String body : List.of("{\"title\":\" \",\"descriptionMd\":\"描述\"}",
                "{\"title\":\"问题\",\"descriptionMd\":\" \"}",
                "{\"title\":\"问题\",\"descriptionMd\":\"描述\",\"priority\":\"P9\"}")) {
            fail(owner, post("/api/workspaces/{id}/bugs", workspaceId).content(body), 400, 40001);
        }
        fail(owner, post("/api/workspaces/{id}/bugs", workspaceId)
                .content("{\"title\":\"大内容\",\"descriptionMd\":\"" + "中".repeat(35000) + "\"}"), 400, 40001);
        fail(owner, get("/api/workspaces/{id}/bugs", workspaceId).param("page", "0"), 400, 40001);
        fail(owner, get("/api/workspaces/{id}/bugs", workspaceId).param("pageSize", "101"), 400, 40001);
        fail(owner, get("/api/workspaces/{id}/bugs", workspaceId).param("status", "INVALID"), 400, 40001);
        fail(owner, get("/api/workspaces/{id}/bugs", workspaceId).param("startDate", "bad-date"), 400, 40001);
        fail(owner, get("/api/workspaces/{id}/bugs", workspaceId)
                .param("startDate", "2026-02-01").param("endDate", "2026-01-01"), 400, 40001);
        long id = create(owner, "{\"title\":\"未指派\",\"descriptionMd\":\"描述\"}");
        fail(owner, post("/api/bugs/{id}/start", id), 400, 42203);
    }

    /** 详情只暴露未删除附件的元数据，存储路径和文件内部名称不可进入响应。 */
    @Test
    protected void 附件摘要排除删除记录与内部路径() throws Exception {
        long id = createAssigned(owner, "附件");
        jdbc.update("""
                INSERT INTO bug_attachment
                    (bug_id, original_name, storage_name, storage_path, file_size, uploader_id, is_deleted, created_at)
                VALUES (?, '截图.png', 'internal.png', '/private/storage/internal.png', 120, ?, 0, CURRENT_TIMESTAMP)
                """, id, owner.id());
        jdbc.update("""
                INSERT INTO bug_attachment
                    (bug_id, original_name, storage_name, storage_path, file_size, uploader_id, is_deleted, created_at)
                VALUES (?, '已删除.png', 'deleted.png', '/private/storage/deleted.png', 120, ?, 1, CURRENT_TIMESTAMP)
                """, id, owner.id());
        ok(developer, get("/api/bugs/{id}", id))
                .andExpect(jsonPath("$.data.attachments.length()").value(1))
                .andExpect(jsonPath("$.data.attachments[0].originalName").value("截图.png"))
                .andExpect(jsonPath("$.data.attachments[0].storagePath").doesNotExist())
                .andExpect(jsonPath("$.data.attachments[0].storageName").doesNotExist());
    }

    /** 两个同时提交的相同旧版本请求只允许一个成功，且只生成一次描述历史。 */
    @Test
    protected void 并发基础信息修改必须检测旧版本() throws Exception {
        long id = createAssigned(owner, "并发编辑");
        List<Integer> statuses = concurrently(
                () -> perform(owner, put("/api/bugs/{id}", id).content(updateBody(0, "并发 A"))).andReturn().getResponse().getStatus(),
                () -> perform(owner, put("/api/bugs/{id}", id).content(updateBody(0, "并发 B"))).andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(count("bug_description_history", id)).isEqualTo(1);
        assertThat(bugs.selectById(id).getVersion()).isEqualTo(1);
    }

    /** 重复并发验收不能重复产生验收记录，即使两个请求都在等待空间锁。 */
    @Test
    protected void 并发验收必须幂等拒绝重复操作() throws Exception {
        long id = createAssigned(owner, "并发验收");
        ok(developer, post("/api/bugs/{id}/start", id));
        fix(developer, id, "修复完成");
        ok(developer, post("/api/bugs/{id}/submit", id));
        List<Integer> statuses = concurrently(
                () -> perform(tester, post("/api/bugs/{id}/accept", id)).andReturn().getResponse().getStatus(),
                () -> perform(tester, post("/api/bugs/{id}/accept", id)).andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(count("bug_acceptance", id)).isEqualTo(1);
    }

    /** Mapper 条件更新应在任何调用路径下拒绝旧版本，不能仅依赖服务中的 Java 比较。 */
    @Test
    protected void 数据库版本条件阻止覆盖() throws Exception {
        long id = createAssigned(owner, "版本条件");
        Bug first = bugs.selectById(id);
        Bug stale = bugs.selectById(id);
        first.setTitle("先写入");
        stale.setTitle("旧版本覆盖");
        assertThat(bugs.updateIfVersionMatches(first)).isEqualTo(1);
        assertThat(bugs.updateIfVersionMatches(stale)).isZero();
        assertThat(bugs.selectById(id).getTitle()).isEqualTo("先写入");
    }

    /** 审计异常必须回滚已插入的历史及标题日志，不能留下部分成功的操作。 */
    @Test
    protected void 审计失败回滚业务和历史() throws Exception {
        long id = createAssigned(owner, "回滚");
        doThrow(new IllegalStateException("测试审计失败"))
                .when(audit).insertLog(any(), anyLong(), eq("UPDATE_DESCRIPTION"), any(), any(), any(), any());
        fail(owner, put("/api/bugs/{id}", id).content(updateBody(0, "应回滚")), 500, 50000);
        assertThat(bugs.selectById(id).getDescriptionMd()).isEqualTo("# 原始描述");
        assertThat(bugs.selectById(id).getVersion()).isZero();
        assertThat(count("bug_description_history", id)).isZero();
        assertThat(count("bug_operation_log", id)).isEqualTo(1);
    }

    /** 验收记录落库失败时，状态和对应日志必须一起回滚。 */
    @Test
    protected void 验收写入失败回滚状态和日志() throws Exception {
        long id = createAssigned(owner, "验收回滚");
        ok(developer, post("/api/bugs/{id}/start", id));
        fix(developer, id, "修复");
        ok(developer, post("/api/bugs/{id}/submit", id));
        int logs = count("bug_operation_log", id);
        doThrow(new IllegalStateException("测试验收写入失败"))
                .when(audit).insertAcceptance(any(), anyLong(), anyString(), any());
        fail(tester, post("/api/bugs/{id}/accept", id), 500, 50000);
        assertThat(bugs.selectById(id).getStatus().name()).isEqualTo("WAIT_ACCEPTANCE");
        assertThat(bugs.selectById(id).getClosedAt()).isNull();
        assertThat(count("bug_operation_log", id)).isEqualTo(logs);
        assertThat(count("bug_acceptance", id)).isZero();
    }

    /** 注册测试用户并保留真实 Token，测试不绕过认证拦截器。 */
    private Session register(String username) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"displayName\":\"%s\",\"password\":\"bugloop123\"}".formatted(username, username)))
                .andExpect(status().isOk());
        return new Session(number(result, "$.data.user.id"),
                JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.token"));
    }

    /** 添加本用例角色，目标仅为当前测试空间。 */
    private void addMember(Session session, String role) throws Exception {
        ok(owner, post("/api/workspaces/{id}/members", workspaceId)
                .content("{\"userId\":%d,\"role\":\"%s\"}".formatted(session.id(), role)));
    }

    /** 创建指定负责人和验收人的标准 Bug。 */
    private long createAssigned(Session creator, String title) throws Exception {
        return create(creator, "{\"title\":\"%s\",\"descriptionMd\":\"# 原始描述\",\"assigneeId\":%d,\"acceptorId\":%d}"
                .formatted(title, developer.id(), tester.id()));
    }

    /** 创建 Bug 并读取主键，不依赖自增初始值。 */
    private long create(Session creator, String body) throws Exception {
        return number(ok(creator, post("/api/workspaces/{id}/bugs", workspaceId).content(body)), "$.data.id");
    }

    /** 保存测试修复说明。 */
    private void fix(Session assignee, long id, String content) throws Exception {
        ok(assignee, put("/api/bugs/{id}/fix-description", id).content("{\"fixDescriptionMd\":\"%s\"}".formatted(content)));
    }

    /** 构造不同版本的基础信息更新请求。 */
    private String updateBody(int version, String description) {
        return "{\"title\":\"修改后的标题\",\"descriptionMd\":\"%s\",\"priority\":\"P1\",\"version\":%d}"
                .formatted(description, version);
    }

    /** 发起真实认证请求，所有测试异常仍由全局处理器转换。 */
    private ResultActions perform(Session session, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + session.token())
                .contentType(MediaType.APPLICATION_JSON));
    }

    /** 验证请求成功并返回断言链。 */
    private ResultActions ok(Session session, MockHttpServletRequestBuilder request) throws Exception {
        return perform(session, request).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    /** 同时验证 HTTP 和业务错误码。 */
    private void fail(Session session, MockHttpServletRequestBuilder request, int http, int code) throws Exception {
        perform(session, request).andExpect(status().is(http)).andExpect(jsonPath("$.code").value(code));
    }

    /** 无损读取 JSON 整数，兼容随机用户主键和自增 Bug 主键。 */
    private long number(ResultActions result, String path) throws Exception {
        Number value = JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
        return value.longValue();
    }

    /** 查询本 Bug 的关联记录数量，表名仅来自测试常量。 */
    private int count(String table, long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE bug_id = ?", Integer.class, id);
    }

    /** 同步释放两个请求并设置超时，证明并发处理不会丢更新或无限等待。 */
    private List<Integer> concurrently(Callable<Integer> first, Callable<Integer> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch gate = new CountDownLatch(1);
        try {
            Future<Integer> a = executor.submit(() -> { gate.await(); return first.call(); });
            Future<Integer> b = executor.submit(() -> { gate.await(); return second.call(); });
            gate.countDown();
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    /** 测试会话，不输出 Token 到应用日志。 */
    private record Session(Long id, String token) {
    }
}
