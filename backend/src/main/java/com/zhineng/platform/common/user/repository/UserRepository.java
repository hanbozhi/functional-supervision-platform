package com.zhineng.platform.common.user.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserRecord> findActiveByUsername(String username) {
        List<UserRecord> users = jdbcTemplate.query(
                """
                SELECT
                  user.id,
                  user.username,
                  user.display_name,
                  user.org_unit_id,
                  org.unit_name
                FROM sys_users user
                LEFT JOIN org_units org ON org.id = user.org_unit_id
                WHERE user.username = ? AND user.status = 'ACTIVE'
                """,
                (resultSet, rowNumber) -> new UserRecord(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("display_name"),
                        nullableLong(resultSet.getObject("org_unit_id")),
                        resultSet.getString("unit_name")
                ),
                username
        );
        return users.stream().findFirst();
    }

    public List<String> findActiveRoleCodes(long userId) {
        return jdbcTemplate.query(
                """
                SELECT role.role_code
                FROM sys_roles role
                JOIN sys_user_roles user_role ON user_role.role_id = role.id
                WHERE user_role.user_id = ? AND role.status = 'ACTIVE'
                ORDER BY role.role_code
                """,
                (resultSet, rowNumber) -> resultSet.getString(1),
                userId
        );
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    public record UserRecord(
            Long id,
            String username,
            String displayName,
            Long orgUnitId,
            String orgUnitName
    ) {
    }
}
