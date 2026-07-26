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
V004__create_three_fixed_plan_tables.sql
V005__create_staffing_ledger_tables.sql
V006__create_core_function_tables.sql
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
| three_fixed_plans | 按机构保存三定方案主档及当前生效版本 |
| three_fixed_plan_versions | 三定方案结构化字段、来源、解析和复核版本 |
| three_fixed_parse_results | 文档字段提取原值、人工修正值和来源片段 |
| three_fixed_field_mappings | XLSX、DOCX、PDF全局标签别名映射 |
| staffing_ledgers | 每个业务机构一条当前编制人员汇总台账 |
| staffing_change_logs | 台账新增、修改、批量修改和Excel导入的永久业务变更记录 |
| staffing_import_batches | 编制人员台账Excel导入批次及成功、失败统计 |
| staffing_import_errors | Excel导入逐行失败原因 |
| department_core_functions | 按机构维护核心职能、行业标签和启停状态 |
| department_duty_items | 三定或手工来源的可匹配职责条目及关键词 |
| org_rights_department_mappings | org_units与权责清单部门名称的人工/自动映射 |
| duty_match_runs | 每次职责关键词匹配的输入签名和汇总结果 |
| duty_match_results | 正常匹配、职责缺失、未核定新增职责及人工复核历史 |
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

## 三定方案信息归集

每个启用机构最多有一个 `three_fixed_plans` 主档，版本保存在
`three_fixed_plan_versions`。同一机构同时只允许一个未确认版本；确认后由主档的
`current_version_id` 指向当前生效版本，不自动回写 `org_units`。

原始上传文件复用 `sys_attachments`：

```text
business_type = THREE_FIXED_PLAN_VERSION
business_id = three_fixed_plan_versions.id
```

附件默认保存在：

```text
backend/storage/three-fixed
```

可通过 `THREE_FIXED_STORAGE_PATH` 覆盖。数据库只保存相对路径，附件目录不纳入Git。
支持 `.xlsx`、`.docx`、`.pdf`，单文件最大10MB，单批最多20个文件且总大小最大50MB。

## 实有人员与领导职数管理

`org_units.approved_staffing` 是核定编制唯一当前值，`staffing_ledgers` 不重复保存该字段。
每个非 `ROOT/GROUP` 机构最多保存一条当前台账；实有在编、领导职数核定/占用和
编外人员保存在 `staffing_ledgers`。修改核定编制时会将机构核验状态重置为 `PENDING`，
但不会回写三定方案版本快照。

每次新增、单条修改、批量修改或Excel导入都会生成 `staffing_change_logs` 记录，
同时写入模块编码为 `M1-4` 的公共操作日志。Excel仅支持 `.xlsx`，单文件最大10MB；
有效行逐条提交，失败行记录在导入错误表中，不影响其他有效行。

## 部门核心职能清单库

m1-5读取已确认三定方案的 `main_responsibilities` 生成可编辑职责候选，也允许完全
手工维护。`rights_items` 作为当前本地“上报履职/权责事项”来源，通过
`org_rights_department_mappings` 与机构关联。自动匹配只使用去重、去停用词后的
人工可编辑关键词，默认阈值为50分；空关键词职责不会产生匹配。

每次匹配写入新的 `duty_match_runs` 和 `duty_match_results`，重新匹配不覆盖历史。
匹配结果允许两侧为空，但业务规则为：`MATCHED` 两侧必须存在，`DUTY_MISSING`
只关联职责，`UNAPPROVED_NEW_DUTY` 只关联权责事项。该完整性由服务层校验。

匹配结果只保存 `rights_items.id` 作为逻辑来源编号，并保存部门、事项名称及内容快照，
不建立指向 `rights_items` 的外键。因此权责清单重新导入仍可清理自身四张表，不会被
m1-5历史结果阻塞；运行记录中的权责数据签名用于提示结果已经过期。

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
