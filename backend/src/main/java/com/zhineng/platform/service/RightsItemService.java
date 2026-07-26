package com.zhineng.platform.service;

import com.zhineng.platform.dto.CountItem;
import com.zhineng.platform.dto.OptionResponse;
import com.zhineng.platform.dto.PageResponse;
import com.zhineng.platform.dto.RightsItemResponse;
import com.zhineng.platform.dto.RightsStatsResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class RightsItemService {

    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;

    public RightsItemService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResponse<RightsItemResponse> page(
            int page,
            int size,
            String keyword,
            String department,
            String powerType,
            String sourceFile
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 5), MAX_PAGE_SIZE);
        int offset = (safePage - 1) * safeSize;

        QueryParts queryParts = buildFilters(keyword, department, powerType, sourceFile);
        Long total = jdbcTemplate.queryForObject(
                "select count(*) from rights_items ri join source_files sf on sf.id = ri.source_file_id "
                        + queryParts.whereClause(),
                Long.class,
                queryParts.params().toArray()
        );

        List<Object> params = new ArrayList<>(queryParts.params());
        params.add(safeSize);
        params.add(offset);

        List<RightsItemResponse> items = jdbcTemplate.query(
                baseSelect()
                        + queryParts.whereClause()
                        + " order by ri.id asc limit ? offset ?",
                this::mapItem,
                params.toArray()
        );

        return PageResponse.of(items, Optional.ofNullable(total).orElse(0L), safePage, safeSize);
    }

    public RightsItemResponse detail(long id) {
        List<RightsItemResponse> items = jdbcTemplate.query(
                baseSelect() + " where ri.id = ?",
                this::mapItem,
                id
        );
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到权责事项");
        }
        return items.get(0);
    }

    public OptionResponse options() {
        return new OptionResponse(
                queryStringList("select distinct department_guess from rights_items where department_guess is not null and trim(department_guess) <> '' order by department_guess"),
                queryStringList("select distinct power_type from rights_items where power_type is not null and trim(power_type) <> '' order by power_type"),
                queryStringList("select file_name from source_files where import_status = 'ok' order by file_name")
        );
    }

    public RightsStatsResponse stats() {
        long totalItems = count("select count(*) from rights_items");
        long totalFiles = count("select count(*) from source_files where import_status = 'ok'");
        long totalDepartments = count("select count(distinct department_guess) from rights_items where department_guess is not null and trim(department_guess) <> ''");
        long totalPowerTypes = count("select count(distinct power_type) from rights_items where power_type is not null and trim(power_type) <> ''");
        return new RightsStatsResponse(
                totalItems,
                totalFiles,
                totalDepartments,
                totalPowerTypes,
                queryCounts("select coalesce(nullif(trim(power_type), ''), '未分类') name, count(*) value from rights_items group by name order by value desc"),
                queryCounts("select coalesce(nullif(trim(department_guess), ''), '未识别部门') name, count(*) value from rights_items group by name order by value desc limit 8")
        );
    }

    private QueryParts buildFilters(String keyword, String department, String powerType, String sourceFile) {
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (hasText(keyword)) {
            conditions.add("(ri.item_name like ? or ri.subitem_name like ? or ri.basis like ? or ri.raw_json like ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (hasText(department)) {
            conditions.add("ri.department_guess = ?");
            params.add(department.trim());
        }
        if (hasText(powerType)) {
            conditions.add("ri.power_type = ?");
            params.add(powerType.trim());
        }
        if (hasText(sourceFile)) {
            conditions.add("sf.file_name = ?");
            params.add(sourceFile.trim());
        }

        String whereClause = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
        return new QueryParts(whereClause, params);
    }

    private String baseSelect() {
        return """
                select
                  ri.id,
                  sf.file_name as source_file,
                  ri.sheet_name,
                  ri.source_row_number,
                  ri.department_guess,
                  ri.year_guess,
                  ri.sequence_no,
                  ri.item_name,
                  ri.subitem_name,
                  ri.power_type,
                  ri.basis,
                  ri.exercising_body,
                  ri.undertaking_org,
                  ri.implementation_level_authority,
                  ri.department_duty,
                  ri.responsibility_content,
                  ri.responsibility_basis,
                  ri.accountability_scope,
                  ri.accountability_situation,
                  ri.remark,
                  ri.status,
                  ri.raw_json
                from rights_items ri
                join source_files sf on sf.id = ri.source_file_id
                """;
    }

    private RightsItemResponse mapItem(ResultSet rs, int rowNum) throws SQLException {
        return new RightsItemResponse(
                rs.getLong("id"),
                rs.getString("source_file"),
                rs.getString("sheet_name"),
                (Integer) rs.getObject("source_row_number"),
                rs.getString("department_guess"),
                rs.getString("year_guess"),
                rs.getString("sequence_no"),
                rs.getString("item_name"),
                rs.getString("subitem_name"),
                rs.getString("power_type"),
                rs.getString("basis"),
                rs.getString("exercising_body"),
                rs.getString("undertaking_org"),
                rs.getString("implementation_level_authority"),
                rs.getString("department_duty"),
                rs.getString("responsibility_content"),
                rs.getString("responsibility_basis"),
                rs.getString("accountability_scope"),
                rs.getString("accountability_situation"),
                rs.getString("remark"),
                rs.getString("status"),
                rs.getString("raw_json")
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private long count(String sql) {
        return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Long.class)).orElse(0L);
    }

    private List<String> queryStringList(String sql) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1));
    }

    private List<CountItem> queryCounts(String sql) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> new CountItem(rs.getString("name"), rs.getLong("value")));
    }

    private record QueryParts(String whereClause, List<Object> params) {
    }
}
