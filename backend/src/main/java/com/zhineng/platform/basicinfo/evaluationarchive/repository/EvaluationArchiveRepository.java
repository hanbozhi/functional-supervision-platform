package com.zhineng.platform.basicinfo.evaluationarchive.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class EvaluationArchiveRepository {
    private static final String COMPLETENESS_SQL = """
            SELECT count(DISTINCT link.category)
            FROM evaluation_archive_attachments link
            JOIN sys_attachments attachment ON attachment.id=link.attachment_id
            WHERE link.archive_id=archive.id AND link.is_current=1
              AND attachment.status='ACTIVE'
              AND link.category IN (
                'REPORT','SELF_ASSESSMENT','RECTIFICATION_LEDGER','REVIEW_RECORD'
              )
            """;

    private final JdbcTemplate jdbc;

    public EvaluationArchiveRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> page(
            Long orgId, Integer year, String type, String grade, String status,
            String accessLevel, String keyword, int limit, int offset
    ) {
        Query query = query(orgId, year, type, grade, status, accessLevel, keyword);
        List<Object> params = new ArrayList<>(query.params);
        params.add(limit);
        params.add(offset);
        String sql = """
                SELECT archive.*, org.unit_code, org.unit_name,
                  creator.display_name created_by_name,
                  updater.display_name updated_by_name,
                  archiver.display_name archived_by_name,
                  (%s) standard_category_count,
                  (SELECT count(*) FROM evaluation_archive_attachments link
                   JOIN sys_attachments attachment ON attachment.id=link.attachment_id
                   WHERE link.archive_id=archive.id AND link.is_current=1
                     AND attachment.status='ACTIVE') attachment_count
                FROM evaluation_archives archive
                JOIN org_units org ON org.id=archive.org_unit_id
                LEFT JOIN sys_users creator ON creator.id=archive.created_by
                LEFT JOIN sys_users updater ON updater.id=archive.updated_by
                LEFT JOIN sys_users archiver ON archiver.id=archive.archived_by
                %s ORDER BY archive.updated_at DESC,archive.id DESC LIMIT ? OFFSET ?
                """.formatted(COMPLETENESS_SQL, query.where);
        return jdbc.queryForList(sql, params.toArray());
    }

    public long count(
            Long orgId, Integer year, String type, String grade, String status,
            String accessLevel, String keyword
    ) {
        Query query = query(orgId, year, type, grade, status, accessLevel, keyword);
        return Optional.ofNullable(jdbc.queryForObject("""
                SELECT count(*) FROM evaluation_archives archive
                JOIN org_units org ON org.id=archive.org_unit_id
                """ + query.where, Long.class, query.params.toArray())).orElse(0L);
    }

    public Map<String, Object> stats() {
        return jdbc.queryForMap("""
                SELECT count(*) total,
                  sum(CASE WHEN status='DRAFT' THEN 1 ELSE 0 END) drafts,
                  sum(CASE WHEN status='ARCHIVED' THEN 1 ELSE 0 END) archived,
                  sum(CASE WHEN (%s)=4 THEN 1 ELSE 0 END) complete,
                  (SELECT count(*) FROM evaluation_archive_attachments link
                   JOIN sys_attachments attachment ON attachment.id=link.attachment_id
                   WHERE link.is_current=1 AND attachment.status='ACTIVE') attachments
                FROM evaluation_archives archive
                """.formatted(COMPLETENESS_SQL));
    }

    public Map<String, Object> find(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT archive.*,org.unit_code,org.unit_name,org.unit_type,org.status org_status,
                  creator.display_name created_by_name,
                  updater.display_name updated_by_name,
                  archiver.display_name archived_by_name,
                  (%s) standard_category_count
                FROM evaluation_archives archive
                JOIN org_units org ON org.id=archive.org_unit_id
                LEFT JOIN sys_users creator ON creator.id=archive.created_by
                LEFT JOIN sys_users updater ON updater.id=archive.updated_by
                LEFT JOIN sys_users archiver ON archiver.id=archive.archived_by
                WHERE archive.id=?
                """.formatted(COMPLETENESS_SQL), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> org(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,unit_type,status FROM org_units WHERE id=?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int nextArchiveSequence(int year) {
        Integer value = jdbc.queryForObject("""
                INSERT INTO evaluation_archive_number_sequences(evaluation_year,last_value)
                VALUES(?,1)
                ON CONFLICT(evaluation_year) DO UPDATE SET last_value=last_value+1
                RETURNING last_value
                """, Integer.class, year);
        return value == null ? 1 : value;
    }

    public long insert(
            String archiveNo, long orgId, int year, String type, String grade,
            String description, String accessLevel, long userId
    ) {
        return insert("""
                INSERT INTO evaluation_archives(
                  archive_no,org_unit_id,evaluation_year,evaluation_type,evaluation_grade,
                  description,access_level,created_by,updated_by
                ) VALUES(?,?,?,?,?,?,?,?,?)
                """, archiveNo, orgId, year, type, grade, description, accessLevel, userId, userId);
    }

    public int update(
            long id, String grade, String description, String accessLevel,
            int rowVersion, long userId
    ) {
        return jdbc.update("""
                UPDATE evaluation_archives
                SET evaluation_grade=?,description=?,access_level=?,updated_by=?,
                    updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),row_version=row_version+1
                WHERE id=? AND status='DRAFT' AND row_version=?
                """, grade, description, accessLevel, userId, id, rowVersion);
    }

    public int archive(long id, int rowVersion, long userId) {
        return jdbc.update("""
                UPDATE evaluation_archives
                SET status='ARCHIVED',archived_by=?,
                    archived_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                    updated_by=?,updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                    row_version=row_version+1
                WHERE id=? AND status='DRAFT' AND row_version=?
                """, userId, userId, id, rowVersion);
    }

    public int withdraw(long id, int rowVersion, long userId) {
        return jdbc.update("""
                UPDATE evaluation_archives
                SET status='DRAFT',archived_by=NULL,archived_at=NULL,updated_by=?,
                    updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),row_version=row_version+1
                WHERE id=? AND status='ARCHIVED' AND row_version=?
                """, userId, id, rowVersion);
    }

    public boolean hasActiveReport(long archiveId) {
        return Optional.ofNullable(jdbc.queryForObject("""
                SELECT count(*) FROM evaluation_archive_attachments link
                JOIN sys_attachments attachment ON attachment.id=link.attachment_id
                WHERE link.archive_id=? AND link.category='REPORT' AND link.is_current=1
                  AND attachment.status='ACTIVE'
                """, Integer.class, archiveId)).orElse(0) > 0;
    }

    public long insertAttachment(
            long archiveId, String originalName, String storedName, String storagePath,
            String contentType, String extension, long size, String sha256,
            int versionNo, long userId
    ) {
        return insert("""
                INSERT INTO sys_attachments(
                  business_type,business_id,original_name,stored_name,storage_path,
                  content_type,extension,file_size,sha256,version_no,uploaded_by,status
                ) VALUES('EVALUATION_ARCHIVE',?,?,?,?,?,?,?,?,?,?,'ACTIVE')
                """, archiveId, originalName, storedName, storagePath, contentType,
                extension, size, sha256, versionNo, userId);
    }

    public long insertAttachmentLink(
            long archiveId, long attachmentId, String category, String group,
            int versionNo, Long previousId, String remarks, long userId
    ) {
        return insert("""
                INSERT INTO evaluation_archive_attachments(
                  archive_id,attachment_id,category,version_group,version_no,
                  previous_relation_id,remarks,created_by
                ) VALUES(?,?,?,?,?,?,?,?)
                """, archiveId, attachmentId, category, group, versionNo,
                previousId, remarks, userId);
    }

    public Map<String, Object> attachment(long relationId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT link.*,attachment.original_name,attachment.stored_name,
                  attachment.storage_path,attachment.content_type,attachment.extension,
                  attachment.file_size,attachment.sha256,attachment.status attachment_status,
                  uploader.display_name uploaded_by_name
                FROM evaluation_archive_attachments link
                JOIN sys_attachments attachment ON attachment.id=link.attachment_id
                LEFT JOIN sys_users uploader ON uploader.id=attachment.uploaded_by
                WHERE link.id=? AND attachment.business_type='EVALUATION_ARCHIVE'
                  AND attachment.business_id=link.archive_id
                """, relationId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> attachments(long archiveId, boolean history) {
        return jdbc.queryForList("""
                SELECT link.id,link.archive_id,link.attachment_id,link.category,
                  link.version_group,link.version_no,link.is_current,
                  link.previous_relation_id,link.remarks,link.created_at,
                  attachment.original_name,attachment.content_type,attachment.extension,
                  attachment.file_size,attachment.status attachment_status,
                  uploader.display_name uploaded_by_name
                FROM evaluation_archive_attachments link
                JOIN sys_attachments attachment ON attachment.id=link.attachment_id
                LEFT JOIN sys_users uploader ON uploader.id=attachment.uploaded_by
                WHERE link.archive_id=? AND (?=1 OR link.is_current=1)
                ORDER BY link.category,link.version_group,link.version_no DESC
                """, archiveId, history ? 1 : 0);
    }

    public void deactivateAttachment(long relationId) {
        jdbc.update("UPDATE evaluation_archive_attachments SET is_current=0 WHERE id=?",
                relationId);
        jdbc.update("""
                UPDATE sys_attachments SET status='INACTIVE',
                  updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now')
                WHERE id=(SELECT attachment_id FROM evaluation_archive_attachments WHERE id=?)
                """, relationId);
    }

    private Query query(
            Long orgId, Integer year, String type, String grade, String status,
            String accessLevel, String keyword
    ) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        add(where, params, " AND archive.org_unit_id=?", orgId);
        add(where, params, " AND archive.evaluation_year=?", year);
        add(where, params, " AND archive.evaluation_type=?", type);
        add(where, params, " AND archive.evaluation_grade=?", grade);
        add(where, params, " AND archive.status=?", status);
        add(where, params, " AND archive.access_level=?", accessLevel);
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            where.append("""
                     AND (archive.archive_no LIKE ? OR org.unit_code LIKE ?
                       OR org.unit_name LIKE ? OR coalesce(archive.description,'') LIKE ?
                       OR EXISTS(
                         SELECT 1 FROM evaluation_archive_attachments keyword_link
                         JOIN sys_attachments keyword_attachment
                           ON keyword_attachment.id=keyword_link.attachment_id
                         WHERE keyword_link.archive_id=archive.id
                           AND keyword_link.is_current=1
                           AND keyword_attachment.status='ACTIVE'
                           AND keyword_attachment.original_name LIKE ?
                       ))
                    """);
            for (int i = 0; i < 5; i++) params.add(like);
        }
        return new Query(where.toString(), params);
    }

    private void add(StringBuilder where, List<Object> params, String sql, Object value) {
        if (value != null && (!(value instanceof String text) || !text.isBlank())) {
            where.append(sql);
            params.add(value);
        }
    }

    private long insert(String sql, Object... params) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement =
                    connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < params.length; i++) statement.setObject(i + 1, params[i]);
            return statement;
        }, keys);
        if (keys.getKey() == null) throw new IllegalStateException("未能获取新增记录ID");
        return keys.getKey().longValue();
    }

    private record Query(String where, List<Object> params) {
    }
}
