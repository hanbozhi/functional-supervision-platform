INSERT OR IGNORE INTO org_units(
  parent_id, unit_code, unit_name, unit_short_name, unit_type,
  unit_level, organization_nature, sort_order, status
) VALUES(
  NULL, 'KLMY-ROOT', '克拉玛依市机构编制体系', '市机构编制体系',
  'ROOT', 'CITY', 'MANAGEMENT_ROOT', 0, 'ACTIVE'
);

INSERT OR IGNORE INTO org_units(
  parent_id, unit_code, unit_name, unit_short_name, unit_type,
  unit_level, organization_nature, sort_order, status
) VALUES(
  (SELECT id FROM org_units WHERE unit_code = 'KLMY-ROOT'),
  'KLMY-BB', '中共克拉玛依市委机构编制委员会办公室', '市委编办',
  'OFFICE', 'CITY', 'PARTY_AGENCY', 10, 'ACTIVE'
);

INSERT OR IGNORE INTO org_units(
  parent_id, unit_code, unit_name, unit_short_name, unit_type,
  unit_level, organization_nature, sort_order, status
) VALUES(
  (SELECT id FROM org_units WHERE unit_code = 'KLMY-ROOT'),
  'KLMY-GOV', '克拉玛依市政府工作部门', '市政府工作部门',
  'GROUP', 'CITY', 'GOVERNMENT_GROUP', 20, 'ACTIVE'
);

INSERT OR IGNORE INTO org_units(
  parent_id, unit_code, unit_name, unit_short_name, unit_type,
  unit_level, organization_nature, sort_order, status
) VALUES
  (
    (SELECT id FROM org_units WHERE unit_code = 'KLMY-GOV'),
    'KLMY-FG', '克拉玛依市发展和改革委员会', '市发展改革委',
    'ADMIN_AGENCY', 'CITY', 'GOVERNMENT_AGENCY', 21, 'ACTIVE'
  ),
  (
    (SELECT id FROM org_units WHERE unit_code = 'KLMY-GOV'),
    'KLMY-CZ', '克拉玛依市财政局', '市财政局',
    'ADMIN_AGENCY', 'CITY', 'GOVERNMENT_AGENCY', 22, 'ACTIVE'
  ),
  (
    (SELECT id FROM org_units WHERE unit_code = 'KLMY-GOV'),
    'KLMY-JY', '克拉玛依市教育局', '市教育局',
    'ADMIN_AGENCY', 'CITY', 'GOVERNMENT_AGENCY', 23, 'ACTIVE'
  ),
  (
    (SELECT id FROM org_units WHERE unit_code = 'KLMY-GOV'),
    'KLMY-WS', '克拉玛依市卫生健康委员会', '市卫生健康委',
    'ADMIN_AGENCY', 'CITY', 'GOVERNMENT_AGENCY', 24, 'ACTIVE'
  );

INSERT OR IGNORE INTO sys_roles(
  role_code, role_name, description, status
) VALUES
  ('SYSTEM_ADMIN', '管理员', '系统公共配置和开发环境管理', 'ACTIVE'),
  ('BUSINESS_ADMIN', '业务管理员', '业务数据维护和流程管理', 'ACTIVE'),
  ('EVALUATOR', '评价人员', '评价任务、评分和复核', 'ACTIVE'),
  ('ORG_OPERATOR', '机构经办人', '机构填报和材料维护', 'ACTIVE');

INSERT OR IGNORE INTO sys_users(
  username, password_hash, display_name, org_unit_id, status
) VALUES(
  'zhang.zhuren',
  NULL,
  '张主任',
  (SELECT id FROM org_units WHERE unit_code = 'KLMY-BB'),
  'ACTIVE'
);

INSERT OR IGNORE INTO sys_user_roles(user_id, role_id, assigned_by)
SELECT user.id, role.id, user.id
FROM sys_users user
JOIN sys_roles role
  ON role.role_code IN ('SYSTEM_ADMIN', 'BUSINESS_ADMIN')
WHERE user.username = 'zhang.zhuren';
