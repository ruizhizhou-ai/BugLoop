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
import org.springframework.mock.web.MockMultipartFile;
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
        systemAdmin = registerSystemAdmin("sysadmin");
        owner = register("owner");
        developer = register("developer");
        tester = register("tester");
        outsider = register("outsider");
        workspaceId = createWorkspaceAsManager(owner, "研发空间");
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
        long otherSpace = createWorkspaceAsManager(outsider, "外部空间");
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
        createWorkspaceAsManager(outsider, "另一个空间");
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

    /** 负责人开始处理后必须冻结；验收人可在提交前调整，但待验收后同样冻结。 */
    @Test
    protected void 人员调整按职责冻结时机执行() throws Exception {
        long id = createAssigned(owner, "人员冻结");
        ok(developer, post("/api/bugs/{id}/start", id))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));

        // 处理中换负责人会中断责任链，服务端必须拒绝，不能仅靠前端隐藏按钮。
        fail(owner, post("/api/bugs/{id}/assign", id)
                .content("{\"assigneeId\":%d}".formatted(tester.id())), 409, 40901);
        // 验收人尚未进入验收阶段，允许调整以应对排班和人员变动。
        ok(owner, post("/api/bugs/{id}/acceptor", id)
                .content("{\"acceptorId\":%d}".formatted(owner.id())))
                .andExpect(jsonPath("$.data.acceptorId").value(owner.id()));

        fix(developer, id, "修复内容");
        ok(developer, post("/api/bugs/{id}/submit", id))
                .andExpect(jsonPath("$.data.status").value("WAIT_ACCEPTANCE"));
        fail(owner, post("/api/bugs/{id}/acceptor", id)
                .content("{\"acceptorId\":%d}".formatted(tester.id())), 409, 40901);
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

    /** 评论、日志、描述历史和验收历史必须受同一工作空间边界保护，并返回实际追溯数据。 */
    @Test
    protected void 追溯接口应返回评论日志历史和验收记录() throws Exception {
        long id = createAssigned(owner, "追溯记录");
        ok(developer, post("/api/bugs/{id}/comments", id)
                .content("{\"contentMd\":\"测试环境也可复现\"}"))
                .andExpect(jsonPath("$.data.displayName").value("developer"))
                .andExpect(jsonPath("$.data.contentMd").value("测试环境也可复现"));
        ok(tester, get("/api/bugs/{id}/comments", id).param("page", "1").param("pageSize", "10"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value("developer"));

        ok(owner, put("/api/bugs/{id}", id).content(updateBody(0, "历史正文")));
        ok(developer, post("/api/bugs/{id}/start", id));
        fix(developer, id, "修复内容");
        ok(developer, post("/api/bugs/{id}/submit", id));
        ok(tester, post("/api/bugs/{id}/accept", id).content("{\"commentMd\":\"验收通过\"}"));

        ok(owner, get("/api/bugs/{id}/logs", id))
                .andExpect(jsonPath("$.data[0].operationType").exists())
                .andExpect(jsonPath("$.data[?(@.operationType == 'ADD_COMMENT')]").isNotEmpty());
        ok(owner, get("/api/bugs/{id}/description-history", id))
                .andExpect(jsonPath("$.data[0].versionNo").value(1))
                .andExpect(jsonPath("$.data[0].contentMd").doesNotExist());
        ok(owner, get("/api/bugs/{id}/description-history/{versionNo}", id, 1))
                .andExpect(jsonPath("$.data.contentMd").value("# 原始描述"));
        ok(owner, get("/api/bugs/{id}/acceptances", id))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].result").value("PASS"))
                .andExpect(jsonPath("$.data[0].acceptorUsername").value("tester"));

        fail(outsider, get("/api/bugs/{id}/comments", id), 403, 40302);
        fail(outsider, get("/api/bugs/{id}/logs", id), 403, 40302);
        fail(outsider, get("/api/bugs/{id}/description-history", id), 403, 40302);
        fail(outsider, get("/api/bugs/{id}/acceptances", id), 403, 40302);
    }

    /** 评论支持多层回复和逻辑删除；界面可压缩层级，但接口必须保留完整直接父子关系。 */
    @Test
    protected void 评论回复删除与权限边界() throws Exception {
        long bugId = createAssigned(owner, "评论闭环");
        long parentCommentId = number(ok(developer, post("/api/bugs/{id}/comments", bugId)
                .content("{\"contentMd\":\"顶级评论\"}")), "$.data.commentId");
        long replyCommentId = number(ok(tester,
                post("/api/bugs/{bugId}/comments/{commentId}/replies", bugId, parentCommentId)
                        .content("{\"contentMd\":\"一级回复\"}")), "$.data.commentId");
        // 父评论作者同样拥有回复权限。
        long selfReplyCommentId = number(ok(developer,
                post("/api/bugs/{bugId}/comments/{commentId}/replies", bugId, parentCommentId)
                        .content("{\"contentMd\":\"补充说明\"}")), "$.data.commentId");

        ok(owner, get("/api/bugs/{id}/comments", bugId))
                .andExpect(jsonPath("$.data.records.length()").value(3))
                .andExpect(jsonPath("$.data.records[0].commentId").value(parentCommentId))
                .andExpect(jsonPath("$.data.records[1].commentId").value(replyCommentId))
                .andExpect(jsonPath("$.data.records[1].parentId").value(parentCommentId))
                .andExpect(jsonPath("$.data.records[1].replyUsername").value("developer"))
                .andExpect(jsonPath("$.data.records[1].parentDeleted").value(false))
                .andExpect(jsonPath("$.data.records[2].commentId").value(selfReplyCommentId))
                .andExpect(jsonPath("$.data.records[2].parentId").value(parentCommentId));

        // 空间负责人同样不能删除平台管理员的发言，避免借由成员管理角色影响管理员留痕。
        long adminCommentId = number(ok(systemAdmin, post("/api/bugs/{id}/comments", bugId)
                .content("{\"contentMd\":\"系统管理员评论\"}")), "$.data.commentId");
        fail(owner, delete("/api/bugs/{bugId}/comments/{commentId}", bugId, adminCommentId), 403, 40301);
        ok(systemAdmin, delete("/api/bugs/{bugId}/comments/{commentId}", bugId, adminCommentId));

        // 回复子评论时记录直接父评论，服务端支持任意层级；前端负责将其压缩为两层展示。
        long nestedReplyId = number(ok(tester,
                post("/api/bugs/{bugId}/comments/{commentId}/replies", bugId, replyCommentId)
                        .content("{\"contentMd\":\"继续跟进\"}")), "$.data.commentId");
        ok(owner, get("/api/bugs/{id}/comments", bugId))
                .andExpect(jsonPath("$.data.records[4].commentId").value(nestedReplyId))
                .andExpect(jsonPath("$.data.records[4].parentId").value(replyCommentId))
                .andExpect(jsonPath("$.data.records[4].replyUsername").value("tester"));
        long anotherBugId = create(owner, "{\"title\":\"其他评论归属\",\"descriptionMd\":\"描述\"}");
        long otherCommentId = number(ok(owner, post("/api/bugs/{id}/comments", anotherBugId)
                .content("{\"contentMd\":\"其他 Bug 评论\"}")), "$.data.commentId");
        fail(tester, post("/api/bugs/{bugId}/comments/{commentId}/replies", bugId, otherCommentId)
                .content("{\"contentMd\":\"跨 Bug 回复\"}"), 400, 42206);

        // 评论删除遵循作者边界：空间 OWNER 也不能代删成员内容，只有平台系统管理员可治理全局内容。
        fail(tester, delete("/api/bugs/{bugId}/comments/{commentId}", bugId, parentCommentId), 403, 40301);
        fail(owner, delete("/api/bugs/{bugId}/comments/{commentId}", bugId, parentCommentId), 403, 40301);
        ok(systemAdmin, delete("/api/bugs/{bugId}/comments/{commentId}", bugId, parentCommentId));
        ok(owner, get("/api/bugs/{id}/comments", bugId))
                .andExpect(jsonPath("$.data.records.length()").value(5))
                .andExpect(jsonPath("$.data.records[0].commentId").value(parentCommentId))
                .andExpect(jsonPath("$.data.records[0].deleted").value(true))
                .andExpect(jsonPath("$.data.records[0].contentMd").doesNotExist())
                .andExpect(jsonPath("$.data.records[1].commentId").value(replyCommentId))
                .andExpect(jsonPath("$.data.records[1].parentDeleted").value(true))
                .andExpect(jsonPath("$.data.records[2].commentId").value(selfReplyCommentId))
                .andExpect(jsonPath("$.data.records[2].parentDeleted").value(true))
                .andExpect(jsonPath("$.data.records[4].commentId").value(nestedReplyId))
                .andExpect(jsonPath("$.data.records[4].parentDeleted").value(false));
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM bug_comment WHERE id = ?", Boolean.class,
                parentCommentId)).isTrue();
        fail(developer, post("/api/bugs/{id}/comments", bugId)
                .content("{\"contentMd\":\"<script>alert(1)</script>\"}"), 400, 40001);
    }

    /** 附件接口应校验扩展名、存储下载、逻辑删除和详情摘要同步，内部路径不能暴露。 */
    @Test
    protected void 附件上传下载和逻辑删除应完整闭环() throws Exception {
        long id = createAssigned(owner, "附件闭环");
        MockMultipartFile textFile = new MockMultipartFile("file", "diagnostic.log", "text/plain",
                "diagnostic content".getBytes());
        mockMvc.perform(multipart("/api/bugs/{id}/attachments", id).file(textFile)
                        .header("Authorization", "Bearer " + developer.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.originalName").value("diagnostic.log"))
                .andExpect(jsonPath("$.data.storagePath").doesNotExist())
                .andExpect(jsonPath("$.data.storageName").doesNotExist());
        long attachmentId = jdbc.queryForObject("SELECT id FROM bug_attachment WHERE bug_id = ?", Long.class, id);

        mockMvc.perform(get("/api/attachments/{id}/download", attachmentId)
                        .header("Authorization", "Bearer " + tester.token()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("diagnostic.log")))
                .andExpect(content().string("diagnostic content"));
        mockMvc.perform(multipart("/api/bugs/{id}/attachments", id)
                        .file(new MockMultipartFile("file", "unsafe.exe", "application/octet-stream", new byte[]{1}))
                        .header("Authorization", "Bearer " + developer.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        ok(owner, delete("/api/attachments/{id}", attachmentId));
        ok(owner, get("/api/bugs/{id}", id)).andExpect(jsonPath("$.data.attachments").isEmpty());
        mockMvc.perform(get("/api/attachments/{id}/download", attachmentId)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40404));
        assertThat(count("bug_operation_log", id)).isEqualTo(3);
    }

    /** 评论与附件写入必须遵守成员边界、空间停用和 Bug 关闭约束；历史附件仍允许上传人清理。 */
    @Test
    protected void 评论与附件写入遵守成员和关闭状态边界() throws Exception {
        long id = createAssigned(owner, "写入边界");
        fail(developer, post("/api/bugs/{id}/comments", id).content("{\"contentMd\":\"  \"}"), 400, 40001);
        fail(outsider, post("/api/bugs/{id}/comments", id).content("{\"contentMd\":\"越界评论\"}"), 403, 40302);
        fail(owner, get("/api/bugs/{id}/comments", id).param("page", "0"), 400, 40001);
        fail(owner, get("/api/bugs/{id}/comments", id).param("pageSize", "101"), 400, 40001);

        upload(id, developer, "diagnostic.log", "content").andExpect(jsonPath("$.code").value(0));
        long attachmentId = jdbc.queryForObject("SELECT id FROM bug_attachment WHERE bug_id = ?", Long.class, id);
        mockMvc.perform(get("/api/attachments/{id}/download", attachmentId)
                        .header("Authorization", "Bearer " + outsider.token()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40302));
        mockMvc.perform(multipart("/api/bugs/{id}/attachments", id)
                        .file(new MockMultipartFile("file", "UPPER.LOG", "text/plain", "x".getBytes()))
                        .header("Authorization", "Bearer " + developer.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.originalName").value("UPPER.LOG"));
        mockMvc.perform(multipart("/api/bugs/{id}/attachments", id)
                        .file(new MockMultipartFile("file", "noext", "text/plain", new byte[]{1}))
                        .header("Authorization", "Bearer " + developer.token()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(multipart("/api/bugs/{id}/attachments", id)
                        .file(new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]))
                        .header("Authorization", "Bearer " + developer.token()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(multipart("/api/bugs/{id}/attachments", id)
                        .file(new MockMultipartFile("file", "outsider.log", "text/plain", new byte[]{1}))
                        .header("Authorization", "Bearer " + outsider.token()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40302));

        ok(owner, post("/api/workspaces/{id}/disable", workspaceId));
        fail(developer, post("/api/bugs/{id}/comments", id).content("{\"contentMd\":\"停用后评论\"}"), 409, 40901);
        mockMvc.perform(multipart("/api/bugs/{id}/attachments", id)
                        .file(new MockMultipartFile("file", "stopped.log", "text/plain", new byte[]{1}))
                        .header("Authorization", "Bearer " + developer.token()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(40901));
        ok(owner, post("/api/workspaces/{id}/enable", workspaceId));

        ok(developer, post("/api/bugs/{id}/start", id));
        fix(developer, id, "修复完成");
        ok(developer, post("/api/bugs/{id}/submit", id));
        ok(tester, post("/api/bugs/{id}/accept", id).content("{\"commentMd\":\"验证通过\"}"));
        ok(developer, post("/api/bugs/{id}/comments", id).content("{\"contentMd\":\"关闭后补充说明\"}"));
        upload(id, developer, "closed.log", "content")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(40901));
        mockMvc.perform(delete("/api/attachments/{id}", attachmentId)
                        .header("Authorization", "Bearer " + developer.token()))
                // 关闭后禁止继续追加普通附件，但不能阻止上传人清理历史附件，避免错误文件永久保留。
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    /** 附件数量上限和历史版本缺失必须返回稳定错误码，而不是落库失败或空响应。 */
    @Test
    protected void 附件数量上限与历史版本缺失() throws Exception {
        long id = createAssigned(owner, "容量边界");
        for (int index = 0; index < 20; index++) {
            jdbc.update("""
                    INSERT INTO bug_attachment
                        (bug_id, original_name, storage_name, storage_path, file_size, uploader_id, is_deleted, created_at)
                    VALUES (?, ?, ?, ?, 1, ?, 0, CURRENT_TIMESTAMP)
                    """, id, "附件%d.log".formatted(index), "internal%d.log".formatted(index),
                    "1/%d/internal%d.log".formatted(id, index), owner.id());
        }
        upload(id, developer, "overflow.log", "content")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(40901));
        jdbc.update("UPDATE bug_attachment SET is_deleted = 1 WHERE bug_id = ? AND original_name = '附件0.log'", id);
        upload(id, developer, "freed.log", "content").andExpect(jsonPath("$.code").value(0));
        fail(owner, get("/api/bugs/{id}/description-history/{versionNo}", id, 99), 404, 40405);
    }

    /** 以指定账号上传一个附件，返回断言链供成功或失败场景复用。 */
    private ResultActions upload(long bugId, Session session, String fileName, String content) throws Exception {
        return mockMvc.perform(multipart("/api/bugs/{id}/attachments", bugId)
                .file(new MockMultipartFile("file", fileName, "text/plain", content.getBytes()))
                .header("Authorization", "Bearer " + session.token()));
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

    /** 注册账号并提升为 SYSTEM_ADMIN；管理员由内置初始化提供，不再由首个注册用户自动获得。 */
    private Session registerSystemAdmin(String username) throws Exception {
        Session session = register(username);
        jdbc.update("UPDATE sys_user SET system_role = 'SYSTEM_ADMIN' WHERE id = ?", session.id());
        return session;
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

    /**
     * 为测试用户临时授予空间管理员身份，再创建目标空间；完成后移除临时关系，
     * 使 SYSTEM_ADMIN 保持非目标空间成员，继续覆盖跨空间只读与创建 Bug 的边界。
     */
    private long createWorkspaceAsManager(Session manager, String name) throws Exception {
        long permissionWorkspaceId = number(ok(systemAdmin, post("/api/workspaces")
                .content("{\"name\":\"%s-权限空间\"}".formatted(name))), "$.data.id");
        ok(systemAdmin, post("/api/workspaces/{id}/members", permissionWorkspaceId)
                .content("{\"userId\":%d,\"role\":\"ADMIN\"}".formatted(manager.id())));
        long targetWorkspaceId = number(ok(manager, post("/api/workspaces")
                .content("{\"name\":\"%s\"}".formatted(name))), "$.data.id");
        ok(systemAdmin, delete("/api/workspaces/{workspaceId}/members/{userId}",
                permissionWorkspaceId, manager.id()));
        return targetWorkspaceId;
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
