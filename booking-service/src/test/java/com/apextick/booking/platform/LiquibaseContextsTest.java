package com.apextick.booking.platform;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The migrations as a deployment that is not the demo runs them, with {@code LIQUIBASE_CONTEXTS=prod}. Nothing had
 * ever run that path, and it still seeded a legacy demo event and its seats: only part of the seed data carried the
 * {@code demo} context. Each context migrates a database of its own, so neither sees the other's rows.
 */
class LiquibaseContextsTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    /** Every table a seed writes to. */
    private static final List<String> SEEDED = List.of("events", "seats", "series", "teams", "price_tiers", "sections");

    @BeforeAll
    static void start() {
        POSTGRES.start();
    }

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    @Test
    void the_prod_context_builds_the_whole_schema_and_seeds_nothing() {
        JdbcClient db = migrated("prod_context", "prod");

        for (String table : SEEDED) {
            assertThat(count(db, "SELECT count(*) FROM " + table)).as(table).isZero();
        }
        // the newest migration ran as well, so the schema is complete rather than merely empty
        assertThat(count(db, "SELECT count(*) FROM information_schema.columns "
                + "WHERE table_name = 'events' AND column_name = 'cancelled_at'")).isEqualTo(1);
    }

    @Test
    void the_demo_context_still_seeds_the_season() {
        JdbcClient db = migrated("demo_context", "demo");

        assertThat(count(db, "SELECT count(*) FROM events")).isPositive();
        assertThat(count(db, "SELECT count(*) FROM seats")).isPositive();
    }

    private static JdbcClient migrated(String database, String contexts) {
        JdbcClient.create(dataSource(POSTGRES.getDatabaseName())).sql("CREATE DATABASE " + database).update();
        DataSource dataSource = dataSource(database);
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.setContexts(contexts);
        liquibase.setResourceLoader(new DefaultResourceLoader());
        try {
            liquibase.afterPropertiesSet();
        } catch (Exception e) {
            throw new IllegalStateException("migrating " + database + " with contexts " + contexts + " failed", e);
        }
        return JdbcClient.create(dataSource);
    }

    private static DataSource dataSource(String database) {
        String url = "jdbc:postgresql://%s:%d/%s".formatted(
                POSTGRES.getHost(), POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT), database);
        return new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static long count(JdbcClient db, String sql) {
        return db.sql(sql).query(Long.class).single();
    }
}
