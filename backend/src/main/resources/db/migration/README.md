# 数据库迁移目录

本目录保存 Flyway SQL Migration，是数据库 Schema 的唯一变更入口。

迁移文件使用 `V{版本号}__{英文说明}.sql` 命名，例如 `V1__create_initial_schema.sql`。已经在共享环境执行的迁移不得修改，应通过新增迁移完成后续调整。

