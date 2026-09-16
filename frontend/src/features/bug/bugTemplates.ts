/**
 * 本文件提供 Bug 创建页使用的内置模板静态来源。
 * 内置模板不写入后端数据库，始终由前端转换为 SYSTEM 范围的统一模板 ViewModel；个人模板则由接口单独加载。
 */
import type { SystemBugTemplateSource } from './bugTemplateApi'

/** 创建页可直接选择的内置 Bug 模板。 */
export const SYSTEM_BUG_TEMPLATES: readonly SystemBugTemplateSource[] = [
  {
    id: 'general-bug',
    name: '常规 Bug',
    title: '请简要描述问题现象',
    descriptionMd: '## 问题现象\n\n## 复现步骤\n1. \n\n## 期望结果\n\n## 实际结果\n',
    priority: 'P2',
    sortOrder: 0,
  },
  {
    id: 'page-error',
    name: '页面异常',
    title: '页面出现异常',
    descriptionMd: '## 异常页面\n\n## 操作步骤\n1. \n\n## 实际表现\n\n## 期望表现\n\n## 浏览器与环境\n',
    priority: 'P2',
    sortOrder: 1,
  },
  {
    id: 'api-error',
    name: '接口异常',
    title: '接口调用异常',
    descriptionMd: '## 请求接口\n\n## 请求参数\n\n## 实际响应\n\n## 期望响应\n\n## 影响范围\n',
    priority: 'P1',
    sortOrder: 2,
  },
]
