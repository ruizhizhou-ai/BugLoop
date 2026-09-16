/**
 * 本文件通过真实 HTTP 请求验证个人 Bug 模板的 CRUD、工作空间隔离和创建人隔离。
 * 测试不绕过认证或服务层，以确保管理员不会因工作空间访问权限而获得其他用户个人模板的读取或管理权限。
 */
package com.wjfz.bugloop.bug.template.controller;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 个人模板 API 端到端测试，覆盖工作空间与创建人两个维度的数据隔离。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BugTemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Session admin;
    private Session userA;
    private Session userB;
    private long primaryWorkspaceId;

    /**
     * 为每个用例创建管理员及两个普通成员，并清理模板相关数据防止测试之间相互影响。
     */
    @BeforeEach
    void prepare() throws Exception {
        for (String table : List.of("bug_template", "workspace_member", "workspace", "sys_user")) {
            jdbc.update("DELETE FROM " + table);
        }
        admin = register("template_admin");
        jdbc.update("UPDATE sys_user SET system_role = 'SYSTEM_ADMIN' WHERE id = ?", admin.id());
        userA = register("template_user_a");
        userB = register("template_user_b");
        primaryWorkspaceId = createWorkspace(admin, "模板主空间");
        addMember(primaryWorkspaceId, userA);
        addMember(primaryWorkspaceId, userB);
    }

    /**
     * 验证个人模板同时按创建人与工作空间隔离，且管理员不能绕过创建人边界。
     */
    @Test
    void 个人模板应隔离创建人与工作空间且删除后不可见() throws Exception {
        long templateA = create(userA, primaryWorkspaceId, "A 的模板", "A 标题");
        long templateB = create(userB, primaryWorkspaceId, "B 的模板", "B 标题");

        // 同一工作空间内，A 只能获得自己的模板，B 的记录不能出现在列表中。
        ok(userA, get("/api/workspaces/{workspaceId}/bug-templates", primaryWorkspaceId))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(templateA))
                .andExpect(jsonPath("$.data[0].creatorId").value(userA.id()));
        ok(userB, get("/api/workspaces/{workspaceId}/bug-templates", primaryWorkspaceId))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(templateB));

        // 管理员可访问工作空间，但个人模板列表仍只能查询其自身创建的记录。
        ok(admin, get("/api/workspaces/{workspaceId}/bug-templates", primaryWorkspaceId))
                .andExpect(jsonPath("$.data").isEmpty());

        // 普通用户和系统管理员都不能修改 B 的个人模板。
        fail(userA, put("/api/bug-templates/{templateId}", templateB)
                .content(updateRequest("越权修改", "越权标题", 2)), 403, 40301);
        fail(admin, put("/api/bug-templates/{templateId}", templateB)
                .content(updateRequest("管理员越权修改", "越权标题", 2)), 403, 40301);

        long secondaryWorkspaceId = createWorkspace(admin, "模板次空间");
        addMember(secondaryWorkspaceId, userB);
        long secondaryTemplate = create(userB, secondaryWorkspaceId, "次空间模板", "次空间标题");

        // B 在主空间查询时不应看到其在次空间创建的个人模板。
        ok(userB, get("/api/workspaces/{workspaceId}/bug-templates", primaryWorkspaceId))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(templateB));

        ok(userB, delete("/api/bug-templates/{templateId}", templateB));
        assertThat(jdbc.queryForObject("SELECT deleted FROM bug_template WHERE id = ?", Boolean.class, templateB))
                .isTrue();
        // @TableLogic 应保证已删除记录在常规列表中完全不可见，且不影响其他工作空间的模板。
        ok(userB, get("/api/workspaces/{workspaceId}/bug-templates", primaryWorkspaceId))
                .andExpect(jsonPath("$.data").isEmpty());
        ok(userB, get("/api/workspaces/{workspaceId}/bug-templates", secondaryWorkspaceId))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(secondaryTemplate));
    }

    /**
     * 验证来源 Bug 的保存权限，以及模板只复制明确允许的字段而不携带处理历史。
     */
    @Test
    void 保存Bug为模板应校验来源权限且只保留模板字段() throws Exception {
        long sourceBugId = createBug(userB);

        // 普通成员不能将他人创建的 Bug 保存为模板。
        fail(userA, post("/api/bugs/{bugId}/save-as-template", sourceBugId)
                .content(saveAsTemplateRequest("普通成员越权模板")), 403, 40301);

        // 将 A 提升为空间管理员后，其可保存当前空间内 B 创建的 Bug，但模板仍归 A 个人所有。
        jdbc.update("UPDATE workspace_member SET role = 'ADMIN' WHERE workspace_id = ? AND user_id = ?",
                primaryWorkspaceId, userA.id());
        ResultActions managerResult = ok(userA, post("/api/bugs/{bugId}/save-as-template", sourceBugId)
                .content(saveAsTemplateRequest("管理员保存模板")));
        long managerTemplateId = number(managerResult, "$.data.id");
        managerResult.andExpect(jsonPath("$.data.workspaceId").value(primaryWorkspaceId))
                .andExpect(jsonPath("$.data.creatorId").value(userA.id()))
                .andExpect(jsonPath("$.data.sourceBugId").value(sourceBugId))
                .andExpect(jsonPath("$.data.name").value("管理员保存模板"))
                .andExpect(jsonPath("$.data.title").value("模板专用标题"))
                .andExpect(jsonPath("$.data.descriptionMd").value("# 模板专用描述"))
                .andExpect(jsonPath("$.data.priority").value("P1"))
                // 模板响应不能包含来源 Bug 的负责人、验收人、状态和附件等历史业务字段。
                .andExpect(jsonPath("$.data.assigneeId").doesNotExist())
                .andExpect(jsonPath("$.data.acceptorId").doesNotExist())
                .andExpect(jsonPath("$.data.status").doesNotExist())
                .andExpect(jsonPath("$.data.attachments").doesNotExist())
                .andExpect(jsonPath("$.data.comments").doesNotExist())
                .andExpect(jsonPath("$.data.operationLogs").doesNotExist())
                .andExpect(jsonPath("$.data.acceptanceRecords").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT source_bug_id FROM bug_template WHERE id = ?", Long.class,
                managerTemplateId)).isEqualTo(sourceBugId);

        // SYSTEM_ADMIN 同样可保存当前空间内的 Bug，但新模板归管理员自己，不能写入 B 的个人模板列表。
        ResultActions systemAdminResult = ok(admin, post("/api/bugs/{bugId}/save-as-template", sourceBugId)
                .content(saveAsTemplateRequest("系统管理员保存模板")));
        systemAdminResult.andExpect(jsonPath("$.data.creatorId").value(admin.id()))
                .andExpect(jsonPath("$.data.sourceBugId").value(sourceBugId));
        ok(userB, get("/api/workspaces/{workspaceId}/bug-templates", primaryWorkspaceId))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    /**
     * 注册测试账号并保留真实认证 Token，确保接口测试经过登录拦截器。
     *
     * @param username 测试用户名
     * @return 用户主键和认证 Token
     */
    private Session register(String username) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"displayName\":\"%s\",\"password\":\"bugloop123\"}"
                                .formatted(username, username)))
                .andExpect(status().isOk());
        return new Session(number(result, "$.data.user.id"),
                JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.token"));
    }

    /**
     * 使用管理员身份创建工作空间，确保后续普通用户只通过成员关系访问该空间。
     *
     * @param manager 管理员会话
     * @param name 工作空间名称
     * @return 新工作空间主键
     */
    private long createWorkspace(Session manager, String name) throws Exception {
        return number(ok(manager, post("/api/workspaces").content("{\"name\":\"%s\"}".formatted(name))), "$.data.id");
    }

    /**
     * 将普通用户添加为工作空间成员，使其可创建和读取自己的个人模板。
     *
     * @param workspaceId 工作空间主键
     * @param member 待添加成员
     */
    private void addMember(long workspaceId, Session member) throws Exception {
        ok(admin, post("/api/workspaces/{workspaceId}/members", workspaceId)
                .content("{\"userId\":%d,\"role\":\"MEMBER\"}".formatted(member.id())));
    }

    /**
     * 创建指定用户在目标工作空间内的个人模板。
     *
     * @param creator 创建人会话
     * @param workspaceId 所属工作空间
     * @param name 模板名称
     * @param title 默认标题
     * @return 新建模板主键
     */
    private long create(Session creator, long workspaceId, String name, String title) throws Exception {
        String body = "{\"name\":\"%s\",\"title\":\"%s\",\"descriptionMd\":\"# 描述\",\"priority\":\"P1\"}"
                .formatted(name, title);
        return number(ok(creator, post("/api/workspaces/{workspaceId}/bug-templates", workspaceId)
                .content(body)), "$.data.id");
    }

    /**
     * 创建一个带负责人和验收人的来源 Bug，验证这些业务字段不会被保存到模板中。
     *
     * @param creator 来源 Bug 创建人
     * @return 新建来源 Bug 主键
     */
    private long createBug(Session creator) throws Exception {
        String body = "{\"title\":\"来源 Bug 标题\",\"descriptionMd\":\"# 来源 Bug 描述\",\"priority\":\"P3\","
                + "\"assigneeId\":%d,\"acceptorId\":%d}".formatted(userA.id(), creator.id());
        return number(ok(creator, post("/api/workspaces/{workspaceId}/bugs", primaryWorkspaceId)
                .content(body)), "$.data.id");
    }

    /**
     * 构造从 Bug 保存模板的请求，字段与来源 Bug 有意不同以验证服务只保存请求白名单。
     *
     * @param name 模板名称
     * @return JSON 请求体
     */
    private String saveAsTemplateRequest(String name) {
        return "{\"name\":\"%s\",\"title\":\"模板专用标题\",\"descriptionMd\":\"# 模板专用描述\",\"priority\":\"P1\"}"
                .formatted(name);
    }

    /**
     * 构造模板更新请求，只包含服务端允许修改的字段。
     *
     * @param name 模板名称
     * @param title 默认标题
     * @param sortOrder 排序值
     * @return JSON 请求体
     */
    private String updateRequest(String name, String title, int sortOrder) {
        return "{\"name\":\"%s\",\"title\":\"%s\",\"descriptionMd\":\"# 新描述\",\"priority\":\"P2\",\"sortOrder\":%d}"
                .formatted(name, title, sortOrder);
    }

    /**
     * 带认证头发起 JSON 请求。
     *
     * @param session 当前用户会话
     * @param request HTTP 请求
     * @return 执行结果
     */
    private ResultActions perform(Session session, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + session.token())
                .contentType(MediaType.APPLICATION_JSON));
    }

    /**
     * 断言接口成功并返回后续 JSON 断言链。
     *
     * @param session 当前用户会话
     * @param request HTTP 请求
     * @return 执行结果
     */
    private ResultActions ok(Session session, MockHttpServletRequestBuilder request) throws Exception {
        return perform(session, request).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    /**
     * 断言接口 HTTP 状态和业务错误码都符合权限限制预期。
     *
     * @param session 当前用户会话
     * @param request HTTP 请求
     * @param httpStatus 预期 HTTP 状态
     * @param errorCode 预期业务错误码
     */
    private void fail(Session session, MockHttpServletRequestBuilder request, int httpStatus, int errorCode)
            throws Exception {
        perform(session, request).andExpect(status().is(httpStatus)).andExpect(jsonPath("$.code").value(errorCode));
    }

    /**
     * 从 JSON 响应读取数值字段，兼容随机用户主键和自增模板主键。
     *
     * @param result HTTP 执行结果
     * @param path JSONPath 路径
     * @return 长整型数值
     */
    private long number(ResultActions result, String path) throws Exception {
        Number value = JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
        return value.longValue();
    }

    /**
     * 测试会话，仅保存用户主键和认证 Token，不输出 Token 到日志。
     *
     * @param id 用户主键
     * @param token 认证 Token
     */
    private record Session(Long id, String token) {
    }
}
