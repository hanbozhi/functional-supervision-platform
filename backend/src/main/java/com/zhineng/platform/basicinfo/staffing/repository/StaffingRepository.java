package com.zhineng.platform.basicinfo.staffing.repository;

import com.zhineng.platform.basicinfo.staffing.dto.StaffingDtos;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class StaffingRepository {
    private final JdbcTemplate jdbc;

    public StaffingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageRows findPage(
            String keyword, String maintenanceStatus, String anomalyStatus, int page, int size
    ) {
        Query query = query(keyword, maintenanceStatus, anomalyStatus);
        long total = Optional.ofNullable(jdbc.queryForObject(
                "SELECT count(*) " + from() + query.where, Long.class, query.params.toArray()
        )).orElse(0L);
        List<Object> params = new ArrayList<>(query.params);
        params.add(size);
        params.add((page - 1) * size);
        List<StaffingDtos.ListItem> items = jdbc.query(
                select() + from() + query.where
                        + " ORDER BY unit.sort_order, unit.id LIMIT ? OFFSET ?",
                this::mapItem, params.toArray());
        return new PageRows(items, total);
    }

    public StaffingDtos.Stats stats(
            String keyword, String maintenanceStatus, String anomalyStatus
    ) {
        Query query = query(keyword, maintenanceStatus, anomalyStatus);
        return jdbc.queryForObject("""
                SELECT
                  coalesce(sum(coalesce(unit.approved_staffing,0)),0) approved_total,
                  coalesce(sum(coalesce(ledger.actual_staffing,0)),0) actual_total,
                  coalesce(sum(coalesce(ledger.external_staff,0)),0) external_total,
                  sum(CASE WHEN ledger.id IS NOT NULL THEN 1 ELSE 0 END) maintained_units,
                  count(*) total_units,
                  sum(CASE WHEN ledger.actual_staffing > coalesce(unit.approved_staffing,0)
                    THEN 1 ELSE 0 END) overstaffed_units,
                  sum(CASE WHEN ledger.leadership_positions_occupied >
                    ledger.leadership_positions_approved THEN 1 ELSE 0 END) leadership_over_units
                """ + from() + query.where, (rs, rowNum) -> {
            long approved = rs.getLong("approved_total");
            long actual = rs.getLong("actual_total");
            return new StaffingDtos.Stats(
                    approved, actual, rs.getLong("external_total"),
                    approved == 0 ? 0 : Math.round(actual * 10000.0 / approved) / 100.0,
                    rs.getLong("maintained_units"), rs.getLong("total_units"),
                    rs.getLong("overstaffed_units"), rs.getLong("leadership_over_units"));
        }, query.params.toArray());
    }

    public Optional<StaffingDtos.ListItem> findById(long id) {
        return jdbc.query(select() + from() + " WHERE ledger.id=?",
                this::mapItem, id).stream().findFirst();
    }

    public Optional<UnitRow> findUnit(long id) {
        return jdbc.query("""
                SELECT id, unit_code, unit_name, unit_type, status, approved_staffing,
                  verification_status, version_no
                FROM org_units WHERE id=?
                """, this::mapUnit, id).stream().findFirst();
    }

    public Optional<UnitRow> findUnitByCode(String code) {
        return jdbc.query("""
                SELECT id, unit_code, unit_name, unit_type, status, approved_staffing,
                  verification_status, version_no
                FROM org_units WHERE unit_code=? COLLATE NOCASE
                """, this::mapUnit, code).stream().findFirst();
    }

    public boolean ledgerExistsForUnit(long orgUnitId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT count(*) > 0 FROM staffing_ledgers WHERE org_unit_id=?",
                Boolean.class, orgUnitId));
    }

    public long insertLedger(LedgerWrite write, long userId) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO staffing_ledgers(
                      org_unit_id, actual_staffing, leadership_positions_approved,
                      leadership_positions_occupied, external_staff, data_date, remarks,
                      last_change_summary, created_by, updated_by
                    ) VALUES(?,?,?,?,?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, write.orgUnitId);
            statement.setInt(2, write.actualStaffing);
            statement.setInt(3, write.leadershipApproved);
            statement.setInt(4, write.leadershipOccupied);
            statement.setInt(5, write.externalStaff);
            statement.setString(6, write.dataDate);
            statement.setString(7, write.remarks);
            statement.setString(8, write.changeReason);
            statement.setLong(9, userId);
            statement.setLong(10, userId);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("新增台账后未获得主键");
        }
        return key.longValue();
    }

    public int updateLedger(long id, LedgerWrite write, long userId, int versionNo) {
        return jdbc.update("""
                UPDATE staffing_ledgers SET
                  actual_staffing=?, leadership_positions_approved=?,
                  leadership_positions_occupied=?, external_staff=?, data_date=?,
                  remarks=?, last_change_summary=?, updated_by=?,
                  updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  version_no=version_no+1
                WHERE id=? AND version_no=?
                """, write.actualStaffing, write.leadershipApproved, write.leadershipOccupied,
                write.externalStaff, write.dataDate, write.remarks, write.changeReason,
                userId, id, versionNo);
    }

    public void updateApprovedStaffing(long orgUnitId, int approved, long userId) {
        jdbc.update("""
                UPDATE org_units SET approved_staffing=?, verification_status='PENDING',
                  verification_opinion=NULL, verified_by=NULL, verified_at=NULL,
                  updated_by=?, updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                  version_no=version_no+1
                WHERE id=?
                """, approved, userId, orgUnitId);
    }

    public void insertChange(ChangeWrite change) {
        jdbc.update("""
                INSERT INTO staffing_change_logs(
                  change_group_no, ledger_id, org_unit_id, change_source,
                  approved_staffing_before, approved_staffing_after,
                  actual_staffing_before, actual_staffing_after,
                  leadership_approved_before, leadership_approved_after,
                  leadership_occupied_before, leadership_occupied_after,
                  external_staff_before, external_staff_after, changed_fields,
                  data_date, change_reason, operator_id
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, change.groupNo, change.ledgerId, change.orgUnitId, change.source,
                change.approvedBefore, change.approvedAfter, change.actualBefore,
                change.actualAfter, change.leadershipApprovedBefore,
                change.leadershipApprovedAfter, change.leadershipOccupiedBefore,
                change.leadershipOccupiedAfter, change.externalBefore, change.externalAfter,
                change.changedFields, change.dataDate, change.reason, change.operatorId);
    }

    public ChangeRows findChanges(long ledgerId, int page, int size) {
        long total = Optional.ofNullable(jdbc.queryForObject(
                "SELECT count(*) FROM staffing_change_logs WHERE ledger_id=?",
                Long.class, ledgerId)).orElse(0L);
        List<StaffingDtos.ChangeLog> rows = jdbc.query("""
                SELECT log.*, user.display_name operator_name
                FROM staffing_change_logs log
                LEFT JOIN sys_users user ON user.id=log.operator_id
                WHERE log.ledger_id=?
                ORDER BY log.operated_at DESC, log.id DESC LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new StaffingDtos.ChangeLog(
                rs.getLong("id"), rs.getString("change_group_no"),
                rs.getString("change_source"), integer(rs.getObject("approved_staffing_before")),
                integer(rs.getObject("approved_staffing_after")),
                integer(rs.getObject("actual_staffing_before")),
                integer(rs.getObject("actual_staffing_after")),
                integer(rs.getObject("leadership_approved_before")),
                integer(rs.getObject("leadership_approved_after")),
                integer(rs.getObject("leadership_occupied_before")),
                integer(rs.getObject("leadership_occupied_after")),
                integer(rs.getObject("external_staff_before")),
                integer(rs.getObject("external_staff_after")),
                rs.getString("changed_fields"), rs.getString("data_date"),
                rs.getString("change_reason"), rs.getString("operator_name"),
                rs.getString("operated_at")
        ), ledgerId, size, (page - 1) * size);
        return new ChangeRows(rows, total);
    }

    public long createImportBatch(
            String batchNo, String fileName, long fileSize, long userId
    ) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO staffing_import_batches(
                      batch_no,file_name,file_size,status,imported_by
                    ) VALUES(?,?,?,'PROCESSING',?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, batchNo);
            statement.setString(2, fileName);
            statement.setLong(3, fileSize);
            statement.setLong(4, userId);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    public void finishImportBatch(
            long id, int total, int success, int failure, int warnings, String status
    ) {
        jdbc.update("""
                UPDATE staffing_import_batches SET total_rows=?,success_rows=?,
                  failed_rows=?,warning_rows=?,status=? WHERE id=?
                """, total, success, failure, warnings, status, id);
    }

    public void insertImportError(
            long batchId, int row, String code, String name, String raw, String message
    ) {
        jdbc.update("""
                INSERT INTO staffing_import_errors(
                  batch_id,row_number,org_unit_code,org_unit_name,raw_data,error_message
                ) VALUES(?,?,?,?,?,?)
                """, batchId, row, code, name, raw, message);
    }

    public Optional<ImportBatch> findImportBatch(long id) {
        return jdbc.query("""
                SELECT * FROM staffing_import_batches WHERE id=?
                """, (rs, rowNum) -> new ImportBatch(
                rs.getLong("id"), rs.getString("batch_no"), rs.getString("file_name"),
                rs.getInt("total_rows"), rs.getInt("success_rows"),
                rs.getInt("failed_rows"), rs.getInt("warning_rows"), rs.getString("status")
        ), id).stream().findFirst();
    }

    public List<StaffingDtos.ImportError> findImportErrors(long id) {
        return jdbc.query("""
                SELECT row_number,org_unit_code,org_unit_name,error_message
                FROM staffing_import_errors WHERE batch_id=? ORDER BY row_number,id
                """, (rs, rowNum) -> new StaffingDtos.ImportError(
                rs.getInt("row_number"), rs.getString("org_unit_code"),
                rs.getString("org_unit_name"), rs.getString("error_message")), id);
    }

    private Query query(String keyword, String maintenanceStatus, String anomalyStatus) {
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        conditions.add("unit.unit_type NOT IN ('ROOT','GROUP')");
        if (keyword != null && !keyword.isBlank()) {
            conditions.add("(unit.unit_name LIKE ? OR unit.unit_code LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
        }
        if ("MAINTAINED".equalsIgnoreCase(maintenanceStatus)) {
            conditions.add("ledger.id IS NOT NULL");
        } else if ("UNMAINTAINED".equalsIgnoreCase(maintenanceStatus)) {
            conditions.add("ledger.id IS NULL");
        }
        if ("OVERSTAFFED".equalsIgnoreCase(anomalyStatus)) {
            conditions.add("ledger.actual_staffing > coalesce(unit.approved_staffing,0)");
        } else if ("LEADERSHIP_OVER".equalsIgnoreCase(anomalyStatus)) {
            conditions.add("ledger.leadership_positions_occupied > ledger.leadership_positions_approved");
        }
        return new Query(" WHERE " + String.join(" AND ", conditions), params);
    }

    private String select() {
        return """
                SELECT ledger.id, unit.id org_unit_id, unit.unit_code, unit.unit_name,
                  unit.unit_type, unit.status unit_status, unit.approved_staffing,
                  ledger.actual_staffing, ledger.leadership_positions_approved,
                  ledger.leadership_positions_occupied, ledger.external_staff,
                  ledger.data_date, ledger.remarks, ledger.last_change_summary,
                  ledger.version_no, updater.display_name updated_by_name, ledger.updated_at
                """;
    }

    private String from() {
        return """
                 FROM org_units unit
                 LEFT JOIN staffing_ledgers ledger ON ledger.org_unit_id=unit.id
                 LEFT JOIN sys_users updater ON updater.id=ledger.updated_by
                """;
    }

    private StaffingDtos.ListItem mapItem(java.sql.ResultSet rs, int rowNum)
            throws java.sql.SQLException {
        Long id = longValue(rs.getObject("id"));
        Integer approved = integer(rs.getObject("approved_staffing"));
        Integer actual = integer(rs.getObject("actual_staffing"));
        Integer leadershipApproved = integer(rs.getObject("leadership_positions_approved"));
        Integer leadershipOccupied = integer(rs.getObject("leadership_positions_occupied"));
        double utilization = approved == null || approved == 0 || actual == null
                ? 0 : Math.round(actual * 10000.0 / approved) / 100.0;
        return new StaffingDtos.ListItem(
                id, rs.getLong("org_unit_id"), rs.getString("unit_code"),
                rs.getString("unit_name"), rs.getString("unit_type"),
                rs.getString("unit_status"), approved, actual, leadershipApproved,
                leadershipOccupied, integer(rs.getObject("external_staff")),
                rs.getString("data_date"), rs.getString("remarks"),
                rs.getString("last_change_summary"), integer(rs.getObject("version_no")),
                rs.getString("updated_by_name"), rs.getString("updated_at"), id != null,
                actual != null && actual > (approved == null ? 0 : approved),
                leadershipOccupied != null && leadershipApproved != null
                        && leadershipOccupied > leadershipApproved,
                utilization);
    }

    private UnitRow mapUnit(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new UnitRow(
                rs.getLong("id"), rs.getString("unit_code"), rs.getString("unit_name"),
                rs.getString("unit_type"), rs.getString("status"),
                integer(rs.getObject("approved_staffing")),
                rs.getString("verification_status"), rs.getInt("version_no"));
    }

    private static Integer integer(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private static Long longValue(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private record Query(String where, List<Object> params) {
    }

    public record PageRows(List<StaffingDtos.ListItem> items, long total) {
    }

    public record ChangeRows(List<StaffingDtos.ChangeLog> items, long total) {
    }

    public record UnitRow(
            long id, String code, String name, String type, String status,
            Integer approvedStaffing, String verificationStatus, int versionNo
    ) {
    }

    public record LedgerWrite(
            long orgUnitId, int actualStaffing, int leadershipApproved,
            int leadershipOccupied, int externalStaff, String dataDate,
            String remarks, String changeReason
    ) {
    }

    public record ChangeWrite(
            String groupNo, long ledgerId, long orgUnitId, String source,
            Integer approvedBefore, Integer approvedAfter,
            Integer actualBefore, Integer actualAfter,
            Integer leadershipApprovedBefore, Integer leadershipApprovedAfter,
            Integer leadershipOccupiedBefore, Integer leadershipOccupiedAfter,
            Integer externalBefore, Integer externalAfter, String changedFields,
            String dataDate, String reason, long operatorId
    ) {
    }

    public record ImportBatch(
            long id, String batchNo, String fileName, int totalRows,
            int successRows, int failedRows, int warningRows, String status
    ) {
    }
}
