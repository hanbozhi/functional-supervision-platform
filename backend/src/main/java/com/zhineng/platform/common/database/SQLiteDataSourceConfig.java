package com.zhineng.platform.common.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration
public class SQLiteDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(SQLiteDataSourceConfig.class);
    private static final String DEFAULT_DATABASE_RELATIVE_PATH = "backend/database/权责清单.sqlite";

    @Bean
    public DataSource dataSource(Environment environment) throws IOException {
        String configuredPath = environment.getProperty("app.database.path");
        Path configured = StringUtils.hasText(configuredPath)
                ? Path.of(configuredPath.trim())
                : null;
        Path projectRoot = configured != null && configured.isAbsolute()
                ? null
                : resolveProjectRoot(Path.of("").toAbsolutePath().normalize());
        Path databasePath = configured != null && configured.isAbsolute()
                ? configured.toAbsolutePath().normalize()
                : resolveDatabasePath(projectRoot, configuredPath);
        Files.createDirectories(databasePath.getParent());

        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setBusyTimeout(10_000);

        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + databasePath);
        log.info("Using SQLite database: {}", databasePath);
        return dataSource;
    }

    static Path resolveProjectRoot(Path workingDirectory) {
        Path rootPom = workingDirectory.resolve("backend").resolve("pom.xml");
        if (Files.isRegularFile(rootPom)) {
            return workingDirectory;
        }

        Path backendPom = workingDirectory.resolve("pom.xml");
        if ("backend".equalsIgnoreCase(String.valueOf(workingDirectory.getFileName()))
                && Files.isRegularFile(backendPom)
                && workingDirectory.getParent() != null) {
            return workingDirectory.getParent();
        }

        throw new IllegalStateException(
                "Unable to locate the project root from " + workingDirectory
                        + ". Start from the project root/backend directory or set PLATFORM_DB_PATH."
        );
    }

    static Path resolveDatabasePath(Path projectRoot, String configuredPath) {
        if (!StringUtils.hasText(configuredPath)) {
            return projectRoot.resolve(DEFAULT_DATABASE_RELATIVE_PATH).toAbsolutePath().normalize();
        }

        Path path = Path.of(configuredPath.trim());
        if (!path.isAbsolute()) {
            path = projectRoot.resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }
}
