# 数据库说明

## 数据库位置

平台当前使用同一份 SQLite 数据库：

```text
backend/database/权责清单.sqlite
```

从项目根目录或 `backend` 目录启动时，后端都会解析到上述同一文件。

可以通过环境变量覆盖路径：

```text
PLATFORM_DB_PATH
```

环境变量可使用绝对路径，也可使用相对于项目根目录的路径。代码和配置中不得写入个人 Windows 绝对路径。

如果没有设置环境变量，且启动目录既不是项目根目录也不是 `backend`，应用会拒绝启动，避免静默创建错误数据库。

## 迁移执行方式

迁移文件位于：

```text
backend/src/main/resources/db/migration
```

命名格式：

```text
V001__create_common_tables.sql
V002__seed_common_development_data.sql
V003__extend_org_units_for_management.sql
```

应用启动时自动按版本号顺序执行。执行记录保存在：

```text
schema_migrations
```

记录内容包括版本、描述、脚本名、SHA-256校验值和执行时间。已执行迁移不会重复执行；已执行文件被修改时，应用会拒绝启动。禁止修改已经执行的迁移，应通过新增更高版本迁移扩展结构。

版本号按数值识别，因此 `V001` 与 `V1` 是同一版本，不得同时存在。迁移检查和执行由同一个 SQLite `BEGIN IMMEDIATE` 事务串行化；多个实例同时启动时，后取得锁的实例会重新读取迁移历史。脚本被改名、修改、删除，或历史中出现数值重复版本时，应用都会拒绝继续迁移。

## 时间和状态约定

公共表时间统一保存为 UTC ISO-8601 文本：

```text
YYYY-MM-DDTHH:MM:SSZ
```

公共业务状态统一使用大写代码：

```text
ACTIVE
INACTIVE
PENDING
DELETED
```

操作结果统一使用：

```text
SUCCESS
FAILURE
```

## 公共表用途

| 表 | 用途 |
|---|---|
| schema_migrations | 数据库迁移执行历史 |
| org_units | 部门、机构及上下级关系 |
| org_unit_verifications | 单位架构历次核验结果、意见、核验人和时间 |
| sys_users | 平台用户；当前仅用于开发模拟和操作人关联 |
| sys_roles | 基础角色 |
| sys_user_roles | 用户与角色多对多关系 |
| sys_attachments | 各业务模块共用的附件元数据 |
| sys_operation_logs | 新增、修改、评分、复核等操作留痕 |

本轮未创建：

```text
sys_permissions
sys_role_permissions
```

## 模拟账号和角色

当前模拟用户由配置集中指定：

```text
MOCK_CURRENT_USERNAME
```

默认值：

```text
zhang.zhuren
```

显示名称为“张主任”，属于“市委编办”，默认具有：

```text
SYSTEM_ADMIN
BUSINESS_ADMIN
```

预置基础角色：

```text
SYSTEM_ADMIN
BUSINESS_ADMIN
EVALUATOR
ORG_OPERATOR
```

当前不包含真实登录认证，也没有预置密码。

这些用户和角色表只用于模拟业务中的操作人、评分人、上传人、复核人和角色展示。项目不继续建设密码登录、会话、Token、权限菜单或接口鉴权；只有页面实际需要时才补充模拟数据。

## 单位架构管理

`org_units.status` 只表示生命周期状态，单位架构页面使用：

```text
ACTIVE
INACTIVE
```

最新业务核验状态保存在 `org_units.verification_status`：

```text
PENDING
VERIFIED
REJECTED
```

历次核验记录保存在 `org_unit_verifications`。机构不做物理删除；存在子机构时禁止停用。创建人、修改人和核验人统一关联 `sys_users` 中的集中模拟用户，当前默认为“张主任”。

`ROOT` 和 `GROUP` 仅用于组织树结构，不计入机构总数、行政机关和事业单位统计。核定编制未填写时保存为 `NULL`，页面不使用原型中的演示数字。

## 权责清单重新导入

导入脚本：

```text
scripts/import_rights_to_sqlite.py
```

默认数据库路径与后端相同，也可以通过 `PLATFORM_DB_PATH` 或 `--db-path` 覆盖。

首次克隆后可以在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/initialize_database.ps1
```

脚本使用 `scripts/requirements.txt` 安装 `openpyxl` 和 `xlrd`，可直接读取原始 `.xls`、`.xlsx`，不依赖临时转换映射。首次启动 Spring Boot 时再自动应用 V001、V002，创建公共表和演示数据。

导入脚本只清理和重新写入以下四张权责清单表的数据：

```text
source_files
raw_rows
rights_items
import_errors
```

它不会删除数据库文件，也不会删除公共表或其他业务表。

重导入采用全有或全无事务：源目录为空、任一文件转换/解析失败，或最终生成 0 条 `rights_items` 时，整次刷新都会回滚，保留导入前的四张权责表数据。只有全部来源文件成功后才提交新数据。

重新导入前应确保：

- 使用 `scripts/database-init.json` 中确定的源文件目录和预期数量；
- 没有其他进程正在批量写入权责清单表；
- 不要恢复旧版“删除整个数据库文件”的逻辑；
- 新业务表暂时不要使用会阻止权责数据刷新的强制外键引用；
- 导入完成后核对 `rights_items`、`raw_rows` 和 `source_files` 数量。
