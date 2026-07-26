# 职能运行监管平台

本项目包含 Vue 前端、Spring Boot 后端和 SQLite 数据库。当前按个人本地开发和演示运行设计，不实现真实登录、密码验证、身份认证或权限控制。

## 环境要求

- JDK 17 或更高版本
- Maven 3.9+
- Node.js 18+ 与 npm
- Python 3.10+
- Windows PowerShell（用于一键初始化脚本）

## 首次初始化数据库

SQLite 工作数据库位于：

```text
backend/database/权责清单.sqlite
```

该文件仅供本机运行，不纳入 Git。数据库可由以下内容重建：

- `backend/src/main/resources/db/migration` 中的全部版本化 SQL（当前 V001-V003）
- `scripts/import_rights_to_sqlite.py`
- `scripts/requirements.txt`
- `资料/各部门网上公示权责清单/各部门网上公示权责清单` 中的原始 Excel

在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/initialize_database.ps1
```

脚本会创建本地 `.venv`、安装 `openpyxl` 和 `xlrd`，然后直接读取 `.xls`、`.xlsx` 原始资料并生成 2943 条权责清单。无需提前用 Excel 转换旧文件。

随后首次启动后端。启动时会自动执行尚未应用的 SQL 迁移，创建公共表并写入演示机构、角色和模拟用户：

```powershell
cd backend
mvn spring-boot:run
```

不要修改、改名或删除已经执行的 V001、V002、V003。后续数据库结构变更必须新增更高版本迁移。

## 启动前端

新开终端，在项目根目录执行：

```powershell
cd frontend
npm ci
npm run dev
```

默认前端地址为 `http://localhost:5173`，后端地址为 `http://localhost:8080`。

## 测试和构建

后端：

```powershell
cd backend
mvn clean package
```

前端：

```powershell
cd frontend
npm ci
npm run build
```

## 本地配置

- `PLATFORM_DB_PATH`：覆盖 SQLite 路径。相对路径按项目根目录解析。
- `MOCK_CURRENT_USERNAME`：覆盖集中模拟用户，默认 `zhang.zhuren`（张主任）。

不要提交 `.env`、密码、Token、API Key、证书或本机绝对路径配置。

## 当前用户和角色原则

- 当前不实现真实认证和权限控制。
- `sys_users`、`sys_roles`、`sys_user_roles` 仅用于模拟操作人、评分人、上传人、复核人和角色展示。
- 业务模块统一使用 `CurrentUserService` 获取“张主任”，不得在各页面或 Service 中重复写死用户。
- 只有页面实际需要时才增加模拟用户或角色字段，不把这些表扩展成认证系统。

更详细的数据库说明见 [docs/database.md](docs/database.md)，暂缓与模拟功能见 [docs/unfinished-features.md](docs/unfinished-features.md)。
