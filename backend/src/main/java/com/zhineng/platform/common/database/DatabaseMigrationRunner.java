package com.zhineng.platform.common.database;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

@Component
public class DatabaseMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationRunner.class);
    private static final Pattern MIGRATION_FILE = Pattern.compile(
            "^V([0-9]+)__(.+)\\.sql$"
    );
    private static final String MIGRATION_LOCATION = "classpath*:db/migration/V*__*.sql";

    private final DataSource dataSource;
    private final PathMatchingResourcePatternResolver resourceResolver =
            new PathMatchingResourcePatternResolver();

    public DatabaseMigrationRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Resource[] resources = resourceResolver.getResources(MIGRATION_LOCATION);
        Arrays.sort(resources, (left, right) ->
                migrationNumber(left).compareTo(migrationNumber(right)));

        Map<String, String> seenVersions = new HashMap<>();
        Migration[] migrations = new Migration[resources.length];
        for (int index = 0; index < resources.length; index++) {
            Migration migration = parseMigration(resources[index]);
            migrations[index] = migration;
            String previousScript = seenVersions.putIfAbsent(
                    migration.version(),
                    migration.scriptName()
            );
            if (previousScript != null) {
                throw new IllegalStateException(
                        "Duplicate database migration version " + migration.version()
                                + ": " + previousScript + " and " + migration.scriptName()
                );
            }
        }

        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getAutoCommit()) {
                connection.setAutoCommit(true);
            }
            executeTransactionControl(connection, "BEGIN IMMEDIATE");
            try {
                ensureMigrationHistoryTable(connection);
                Map<String, AppliedMigration> applied = loadAppliedMigrations(connection);
                Set<String> availableVersions = new HashSet<>();
                for (Migration migration : migrations) {
                    availableVersions.add(migration.version());
                    applyIfNeeded(connection, migration, applied.get(migration.version()));
                }
                for (AppliedMigration history : applied.values()) {
                    if (!availableVersions.contains(history.version())) {
                        throw new IllegalStateException(
                                "Previously executed migration script is missing: "
                                        + history.scriptName()
                        );
                    }
                }
                executeTransactionControl(connection, "COMMIT");
            } catch (Exception | Error exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    private void ensureMigrationHistoryTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_migrations (
                      version TEXT PRIMARY KEY,
                      description TEXT NOT NULL,
                      script_name TEXT NOT NULL UNIQUE,
                      checksum_sha256 TEXT NOT NULL,
                      executed_at TEXT NOT NULL
                        DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
                    )
                    """);
        }
    }

    private Map<String, AppliedMigration> loadAppliedMigrations(Connection connection)
            throws SQLException {
        Map<String, AppliedMigration> applied = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT version, description, script_name, checksum_sha256
                     FROM schema_migrations
                     """)) {
            while (resultSet.next()) {
                String storedVersion = resultSet.getString("version");
                String canonicalVersion = canonicalVersion(storedVersion);
                AppliedMigration previous = applied.putIfAbsent(
                        canonicalVersion,
                        new AppliedMigration(
                                canonicalVersion,
                                resultSet.getString("description"),
                                resultSet.getString("script_name"),
                                resultSet.getString("checksum_sha256")
                        )
                );
                if (previous != null) {
                    throw new IllegalStateException(
                            "Duplicate numeric migration version in history: "
                                    + storedVersion + " conflicts with " + previous.scriptName()
                    );
                }
            }
        }
        return applied;
    }

    private void applyIfNeeded(
            Connection connection,
            Migration migration,
            AppliedMigration applied
    ) throws SQLException {
        if (applied != null) {
            if (!applied.scriptName().equals(migration.scriptName())
                    || !applied.description().equals(migration.description())
                    || !applied.checksum().equals(migration.checksum())) {
                throw new IllegalStateException(
                        "Previously executed migration metadata or content changed: "
                                + applied.scriptName()
                );
            }
            log.debug("Database migration {} already applied", migration.scriptName());
            return;
        }

        ScriptUtils.executeSqlScript(connection, migration.resource());
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO schema_migrations(
                  version, description, script_name, checksum_sha256
                ) VALUES(?,?,?,?)
                """)) {
            statement.setString(1, migration.version());
            statement.setString(2, migration.description());
            statement.setString(3, migration.scriptName());
            statement.setString(4, migration.checksum());
            statement.executeUpdate();
        }
        log.info("Applied database migration {}", migration.scriptName());
    }

    private void executeTransactionControl(Connection connection, String sql)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void rollback(Connection connection, Throwable original) {
        try {
            executeTransactionControl(connection, "ROLLBACK");
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private String canonicalVersion(String version) {
        try {
            return new BigInteger(version).toString();
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "Invalid migration version in schema_migrations: " + version,
                    exception
            );
        }
    }

    private BigInteger migrationNumber(Resource resource) {
        String fileName = requireFilename(resource);
        Matcher matcher = MIGRATION_FILE.matcher(fileName);
        if (!matcher.matches()) {
            throw new IllegalStateException("Invalid migration file name: " + fileName);
        }
        return new BigInteger(matcher.group(1));
    }

    private Migration parseMigration(Resource resource) throws IOException {
        String fileName = requireFilename(resource);
        Matcher matcher = MIGRATION_FILE.matcher(fileName);
        if (!matcher.matches()) {
            throw new IllegalStateException("Invalid migration file name: " + fileName);
        }
        String description = matcher.group(2).replace('_', ' ');
        return new Migration(
                canonicalVersion(matcher.group(1)),
                description,
                fileName,
                sha256(resource),
                resource
        );
    }

    private String requireFilename(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null) {
            throw new IllegalStateException("Migration resource has no filename: " + resource);
        }
        return filename;
    }

    private String sha256(Resource resource) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = resource.getInputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record Migration(
            String version,
            String description,
            String scriptName,
            String checksum,
            Resource resource
    ) {
    }

    private record AppliedMigration(
            String version,
            String description,
            String scriptName,
            String checksum
    ) {
    }
}
