package com.zhineng.platform.common.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zhineng.platform.common.user.repository.UserRepository;
import com.zhineng.platform.common.user.service.CurrentUserService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

class DatabaseInfrastructureTest {

    @TempDir
    Path tempDirectory;

    @Test
    void migrationsAreAppliedOnceAndSeedTheMockUser() throws Exception {
        Path databasePath = tempDirectory.resolve("migration-test.sqlite");
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + databasePath);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(dataSource);

        runner.run(new DefaultApplicationArguments(new String[0]));
        runner.run(new DefaultApplicationArguments(new String[0]));

        assertEquals(
                2,
                jdbcTemplate.queryForObject("SELECT count(*) FROM schema_migrations", Integer.class)
        );
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM sys_users WHERE username = 'zhang.zhuren'",
                        Integer.class
                )
        );
        assertEquals(
                4,
                jdbcTemplate.queryForObject("SELECT count(*) FROM sys_roles", Integer.class)
        );
        assertEquals(
                1,
                jdbcTemplate.queryForObject("PRAGMA foreign_keys", Integer.class)
        );

        var currentUser = new CurrentUserService(
                new UserRepository(jdbcTemplate),
                "zhang.zhuren"
        ).getCurrentUser();
        assertEquals("zhang.zhuren", currentUser.username());
        assertEquals(List.of("BUSINESS_ADMIN", "SYSTEM_ADMIN"), currentUser.roleCodes());

        assertEquals(
                1,
                jdbcTemplate.update("""
                        UPDATE schema_migrations
                        SET checksum_sha256 = 'tampered'
                        WHERE script_name = 'V001__create_common_tables.sql'
                        """)
        );
        assertThrows(
                IllegalStateException.class,
                () -> runner.run(new DefaultApplicationArguments(new String[0]))
        );
    }

    @Test
    void configuredDataSourceEnablesForeignKeys() throws Exception {
        Path databasePath = tempDirectory.resolve("configured-data-source.sqlite");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.database.path", databasePath.toString());

        DataSource dataSource = new SQLiteDataSourceConfig().dataSource(environment);

        assertEquals(
                1,
                new JdbcTemplate(dataSource).queryForObject(
                        "PRAGMA foreign_keys",
                        Integer.class
                )
        );
    }

    @Test
    void concurrentMigrationRunnersSerializeOnSQLite() throws Exception {
        Path databasePath = tempDirectory.resolve("concurrent-migrations.sqlite");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.database.path", databasePath.toString());
        DataSource dataSource = new SQLiteDataSourceConfig().dataSource(environment);
        Callable<Void> migration = () -> {
            new DatabaseMigrationRunner(dataSource)
                    .run(new DefaultApplicationArguments(new String[0]));
            return null;
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(migration);
            var second = executor.submit(migration);
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }

        assertEquals(
                2,
                new JdbcTemplate(dataSource).queryForObject(
                        "SELECT count(*) FROM schema_migrations",
                        Integer.class
                )
        );
    }

    @Test
    void rootAndBackendWorkingDirectoriesResolveToTheSameDatabase() throws Exception {
        Path projectRoot = tempDirectory.resolve("project");
        Path backend = projectRoot.resolve("backend");
        Files.createDirectories(backend);
        Files.createFile(backend.resolve("pom.xml"));

        assertEquals(projectRoot, SQLiteDataSourceConfig.resolveProjectRoot(projectRoot));
        assertEquals(projectRoot, SQLiteDataSourceConfig.resolveProjectRoot(backend));
        assertEquals(
                projectRoot.resolve("backend/database/权责清单.sqlite"),
                SQLiteDataSourceConfig.resolveDatabasePath(projectRoot, null)
        );
    }

    @Test
    void unknownWorkingDirectoryIsRejected() {
        Path unknown = tempDirectory.resolve("unknown");
        assertThrows(
                IllegalStateException.class,
                () -> SQLiteDataSourceConfig.resolveProjectRoot(unknown)
        );
    }
}
