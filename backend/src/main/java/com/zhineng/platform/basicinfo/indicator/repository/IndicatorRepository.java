package com.zhineng.platform.basicinfo.indicator.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class IndicatorRepository {
    private final JdbcTemplate jdbc;

    public IndicatorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> systems(String keyword, Integer year, String status) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        var params = new java.util.ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (system.system_code LIKE ? OR system.system_name LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
        }
        if (year != null) {
            where.append(" AND EXISTS(SELECT 1 FROM indicator_versions v"
                    + " WHERE v.system_id=system.id AND v.evaluation_year=?)");
            params.add(year);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND system.status=?");
            params.add(status);
        }
        return jdbc.queryForList("""
                SELECT system.*,
                  (SELECT count(*) FROM indicator_versions v WHERE v.system_id=system.id)
                    version_count,
                  (SELECT count(*) FROM indicator_versions v WHERE v.system_id=system.id
                    AND v.status='PUBLISHED') published_count,
                  (SELECT count(*) FROM indicator_items item
                    JOIN indicator_versions v ON v.id=item.version_id
                    WHERE v.system_id=system.id) indicator_count
                FROM indicator_systems system
                """ + where + " ORDER BY system.updated_at DESC,system.id DESC",
                params.toArray());
    }

    public Map<String, Object> system(long id) {
        return one("SELECT * FROM indicator_systems WHERE id=?", id);
    }

    public boolean systemCodeExists(String code) {
        return count("SELECT count(*) FROM indicator_systems WHERE system_code=? COLLATE NOCASE",
                code) > 0;
    }

    public long insertSystem(
            String code, String name, String orgType, String description, long userId
    ) {
        return insert("""
                INSERT INTO indicator_systems(
                  system_code,system_name,applicable_org_type,description,created_by,updated_by
                ) VALUES(?,?,?,?,?,?)
                """, code, name, orgType, description, userId, userId);
    }

    public List<Map<String, Object>> versions(Long systemId, Integer year, String status) {
        StringBuilder sql = new StringBuilder("""
                SELECT version.*,system.system_code,system.system_name,
                  system.applicable_org_type,
                  (SELECT count(*) FROM indicator_items item WHERE item.version_id=version.id)
                    indicator_count,
                  (SELECT count(*) FROM indicator_items item WHERE item.version_id=version.id
                    AND item.indicator_level=3 AND item.status='ACTIVE') scoring_item_count
                FROM indicator_versions version
                JOIN indicator_systems system ON system.id=version.system_id WHERE 1=1
                """);
        var params = new java.util.ArrayList<>();
        if (systemId != null) { sql.append(" AND version.system_id=?"); params.add(systemId); }
        if (year != null) { sql.append(" AND version.evaluation_year=?"); params.add(year); }
        if (status != null && !status.isBlank()) {
            sql.append(" AND version.status=?"); params.add(status);
        }
        sql.append(" ORDER BY version.evaluation_year DESC,version.version_no DESC");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public Map<String, Object> version(long id) {
        return one("""
                SELECT version.*,system.system_code,system.system_name,
                  system.applicable_org_type,system.description system_description
                FROM indicator_versions version
                JOIN indicator_systems system ON system.id=version.system_id
                WHERE version.id=?
                """, id);
    }

    public int nextVersionNo(long systemId, int year) {
        Integer value = jdbc.queryForObject("""
                SELECT coalesce(max(version_no),0)+1 FROM indicator_versions
                WHERE system_id=? AND evaluation_year=?
                """, Integer.class, systemId, year);
        return value == null ? 1 : value;
    }

    public long insertVersion(
            long systemId, int year, int versionNo, String name,
            Long sourceVersionId, long userId
    ) {
        return insert("""
                INSERT INTO indicator_versions(
                  system_id,evaluation_year,version_no,version_name,source_version_id,
                  created_by,updated_by
                ) VALUES(?,?,?,?,?,?,?)
                """, systemId, year, versionNo, name, sourceVersionId, userId, userId);
    }

    public int publish(long id, int rowVersion, long userId) {
        return jdbc.update("""
                UPDATE indicator_versions SET status='PUBLISHED',published_by=?,
                  published_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  updated_by=?,updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  row_version=row_version+1
                WHERE id=? AND status='DRAFT' AND row_version=?
                """, userId, userId, id, rowVersion);
    }

    public int archive(long id, int rowVersion, long userId) {
        return jdbc.update("""
                UPDATE indicator_versions SET status='ARCHIVED',archived_by=?,
                  archived_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  updated_by=?,updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  row_version=row_version+1
                WHERE id=? AND status='PUBLISHED' AND row_version=?
                """, userId, userId, id, rowVersion);
    }

    public List<Map<String, Object>> items(long versionId) {
        return jdbc.queryForList("""
                SELECT item.*,
                  (SELECT count(*) FROM indicator_scoring_rules rule
                   WHERE rule.indicator_id=item.id AND rule.status='ACTIVE') active_rule_count
                FROM indicator_items item WHERE item.version_id=?
                ORDER BY item.indicator_level,item.sort_order,item.id
                """, versionId);
    }

    public Map<String, Object> item(long id) {
        return one("""
                SELECT item.*,version.status version_status,version.system_id
                FROM indicator_items item
                JOIN indicator_versions version ON version.id=item.version_id
                WHERE item.id=?
                """, id);
    }

    public boolean itemCodeExists(long versionId, String code, Long excludeId) {
        return count("""
                SELECT count(*) FROM indicator_items
                WHERE version_id=? AND indicator_code=? COLLATE NOCASE
                  AND (? IS NULL OR id<>?)
                """, versionId, code, excludeId, excludeId) > 0;
    }

    public long insertItem(
            long versionId, Long parentId, int level, String code, String name,
            double score, double weight, String type, String method,
            int sortOrder, long userId
    ) {
        return insert("""
                INSERT INTO indicator_items(
                  version_id,parent_id,parent_version_id,parent_level,
                  indicator_level,indicator_code,indicator_name,
                  standard_score,weight,indicator_type,evaluation_method,sort_order,
                  created_by,updated_by
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, versionId, parentId, parentId == null ? null : versionId,
                parentId == null ? null : level - 1, level, code, name, score, weight,
                type, method, sortOrder, userId, userId);
    }

    public int updateItem(
            long id, Long parentId, String code, String name, double score,
            double weight, String type, String method, int sortOrder,
            int rowVersion, long userId
    ) {
        return jdbc.update("""
                UPDATE indicator_items SET parent_id=?,
                  parent_version_id=CASE WHEN ? IS NULL THEN NULL ELSE version_id END,
                  parent_level=CASE WHEN ? IS NULL THEN NULL ELSE indicator_level-1 END,
                  indicator_code=?,indicator_name=?,
                  standard_score=?,weight=?,indicator_type=?,evaluation_method=?,sort_order=?,
                  updated_by=?,updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  row_version=row_version+1
                WHERE id=? AND row_version=?
                  AND EXISTS(SELECT 1 FROM indicator_versions v
                    WHERE v.id=indicator_items.version_id AND v.status='DRAFT')
                """, parentId, parentId, parentId, code, name, score, weight,
                type, method, sortOrder,
                userId, id, rowVersion);
    }

    public int itemStatus(long id, String status, int rowVersion, long userId) {
        return jdbc.update("""
                UPDATE indicator_items SET status=?,updated_by=?,
                  updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  row_version=row_version+1
                WHERE id=? AND row_version=?
                  AND EXISTS(SELECT 1 FROM indicator_versions v
                    WHERE v.id=indicator_items.version_id AND v.status='DRAFT')
                """, status, userId, id, rowVersion);
    }

    public long itemChildCount(long id) {
        return count("SELECT count(*) FROM indicator_items WHERE parent_id=? AND status='ACTIVE'", id);
    }

    public List<Map<String, Object>> rules(Long versionId, Long indicatorId) {
        String sql = """
                SELECT rule.*,item.indicator_code,item.indicator_name,item.version_id,
                  version.status version_status
                FROM indicator_scoring_rules rule
                JOIN indicator_items item ON item.id=rule.indicator_id
                JOIN indicator_versions version ON version.id=item.version_id
                WHERE (? IS NULL OR item.version_id=?)
                  AND (? IS NULL OR rule.indicator_id=?)
                ORDER BY item.sort_order,rule.sort_order,rule.id
                """;
        return jdbc.queryForList(sql, versionId, versionId, indicatorId, indicatorId);
    }

    public Map<String, Object> rule(long id) {
        return one("""
                SELECT rule.*,item.indicator_level,item.version_id,
                  version.status version_status
                FROM indicator_scoring_rules rule
                JOIN indicator_items item ON item.id=rule.indicator_id
                JOIN indicator_versions version ON version.id=item.version_id
                WHERE rule.id=?
                """, id);
    }

    public long insertRule(
            long indicatorId, String type, String name, String config,
            String description, int sortOrder, long userId
    ) {
        return insert("""
                INSERT INTO indicator_scoring_rules(
                  indicator_id,rule_type,rule_name,config_json,description,sort_order,
                  created_by,updated_by
                ) VALUES(?,?,?,?,?,?,?,?)
                """, indicatorId, type, name, config, description, sortOrder, userId, userId);
    }

    public int updateRule(
            long id, long indicatorId, String type, String name, String config,
            String description, int sortOrder, int rowVersion, long userId
    ) {
        return jdbc.update("""
                UPDATE indicator_scoring_rules SET indicator_id=?,rule_type=?,rule_name=?,config_json=?,
                  description=?,sort_order=?,updated_by=?,
                  updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  row_version=row_version+1
                WHERE id=? AND row_version=?
                  AND EXISTS(SELECT 1 FROM indicator_items item
                    JOIN indicator_versions v ON v.id=item.version_id
                    WHERE item.id=indicator_scoring_rules.indicator_id AND v.status='DRAFT')
                """, indicatorId, type, name, config, description, sortOrder,
                userId, id, rowVersion);
    }

    public int ruleStatus(long id, String status, int rowVersion, long userId) {
        return jdbc.update("""
                UPDATE indicator_scoring_rules SET status=?,updated_by=?,
                  updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  row_version=row_version+1
                WHERE id=? AND row_version=?
                  AND EXISTS(SELECT 1 FROM indicator_items item
                    JOIN indicator_versions v ON v.id=item.version_id
                    WHERE item.id=indicator_scoring_rules.indicator_id AND v.status='DRAFT')
                """, status, userId, id, rowVersion);
    }

    public List<Map<String, Object>> templates(String keyword, String orgType, String status) {
        StringBuilder sql = new StringBuilder("""
                SELECT template.*,creator.display_name created_by_name
                FROM indicator_templates template
                LEFT JOIN sys_users creator ON creator.id=template.created_by WHERE 1=1
                """);
        var params = new java.util.ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (template.template_code LIKE ? OR template.template_name LIKE ?)");
            String like = "%" + keyword.trim() + "%"; params.add(like); params.add(like);
        }
        if (orgType != null && !orgType.isBlank()) {
            sql.append(" AND template.applicable_org_type=?"); params.add(orgType);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND template.status=?"); params.add(status);
        }
        sql.append(" ORDER BY template.updated_at DESC,template.id DESC");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public Map<String, Object> template(long id) {
        return one("SELECT * FROM indicator_templates WHERE id=?", id);
    }

    public boolean templateCodeExists(String code) {
        return count("SELECT count(*) FROM indicator_templates"
                + " WHERE template_code=? COLLATE NOCASE", code) > 0;
    }

    public long insertTemplate(
            String code, String name, String orgType, String description,
            String snapshot, Long sourceVersionId, int count, long userId
    ) {
        return insert("""
                INSERT INTO indicator_templates(
                  template_code,template_name,applicable_org_type,description,snapshot_json,
                  source_version_id,indicator_count,created_by,updated_by
                ) VALUES(?,?,?,?,?,?,?,?,?)
                """, code, name, orgType, description, snapshot,
                sourceVersionId, count, userId, userId);
    }

    public int templateStatus(long id, String status, int rowVersion, long userId) {
        return jdbc.update("""
                UPDATE indicator_templates SET status=?,updated_by=?,
                  updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  row_version=row_version+1 WHERE id=? AND row_version=?
                """, status, userId, id, rowVersion);
    }

    private long insert(String sql, Object... args) {
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement =
                    connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, holder);
        if (holder.getKey() == null) throw new IllegalStateException("未获取新增记录ID");
        return holder.getKey().longValue();
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
