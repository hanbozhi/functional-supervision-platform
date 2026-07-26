package com.zhineng.platform.common.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OperationLogRepository {
    private final JdbcTemplate jdbcTemplate;

    public OperationLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void success(
            String moduleCode,
            String businessType,
            long businessId,
            String action,
            long operatorId,
            String method,
            String path,
            String beforeJson,
            String afterJson
    ) {
        jdbcTemplate.update("""
                INSERT INTO sys_operation_logs(
                  module_code, business_type, business_id, action, operator_id,
                  request_method, request_path, before_json, after_json, result
                ) VALUES(?,?,?,?,?,?,?,?,?,'SUCCESS')
                """, moduleCode, businessType, businessId, action, operatorId,
                method, path, beforeJson, afterJson);
    }
}
