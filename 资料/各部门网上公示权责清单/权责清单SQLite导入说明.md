# 各部门网上公示权责清单 SQLite 导入说明

## 生成文件

- 数据库文件：`backend/database/权责清单.sqlite`
- 导入脚本：`scripts/import_rights_to_sqlite.py`
- 源文件目录：`资料/各部门网上公示权责清单/各部门网上公示权责清单`

## 导入结果

- 表格文件总数：22
- 成功入库：22
- 导入失败：0
- 使用原始格式直接重建时识别的原始有效行数：3011
- 标准化权责事项数：2943

## 数据表说明

- `source_files`：记录每个来源文件的文件名、路径、扩展名、哈希值、推断部门、推断年份、导入状态和错误信息。
- `raw_rows`：保存每个工作表中的非空原始行，使用 JSON 保留原始列名和值。
- `rights_items`：按统一字段结构保存可识别的权责事项数据。
- `import_errors`：记录导入或转换失败的文件及错误原因。

## 标准化字段

`rights_items` 表包含以下主要业务字段：

- `sequence_no`：序号
- `item_name`：事项名称
- `subitem_name`：子项名称
- `power_type`：权力类型
- `basis`：实施依据
- `exercising_body`：行使主体
- `undertaking_org`：承办机构
- `implementation_level_authority`：实施层级及权限
- `department_duty`：部门职责
- `responsibility_content`：责任事项内容
- `responsibility_basis`：责任事项依据
- `accountability_scope`：追责对象范围
- `accountability_situation`：追责情形
- `remark`：备注
- `status`：状态

## 备注

导入器同时使用 `openpyxl` 和 `xlrd`，能够按文件内容识别 OOXML 与旧版 OLE 工作簿，不再需要 Excel 转换映射。安装依赖和重建数据库可执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/initialize_database.ps1
```

以下两个文件虽然后缀为 `.xlsx`，但内部仍是旧版 OLE 表格格式，导入器会自动使用 `xlrd`：

- `附件：克拉玛依市财政局2025年权责清单 (1).xlsx`
- `克拉玛依市2025年消防监督执法权责清单.xlsx`
