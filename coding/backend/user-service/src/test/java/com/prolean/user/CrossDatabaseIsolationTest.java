package com.prolean.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Proves the deployment's PostgreSQL isolation mechanism: a role that owns one database cannot
 * connect to another database owned by a different role.
 *
 * <p>This is a cross-database denial test, separate from the service-level integration tests. It
 * intentionally uses a dedicated container so it does not rely on Compose being available in CI.
 */
class CrossDatabaseIsolationTest {

    @Test
    void roleCannotConnectToAnotherServiceDatabase() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")) {
            postgres.start();
            try (Connection admin = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                    Statement statement = admin.createStatement()) {
                statement.execute("CREATE ROLE alice LOGIN PASSWORD 'alice-password'");
                statement.execute("CREATE ROLE bob LOGIN PASSWORD 'bob-password'");
                statement.execute("CREATE DATABASE alice_db OWNER alice");
                statement.execute("CREATE DATABASE bob_db OWNER bob");
                statement.execute("REVOKE CONNECT ON DATABASE alice_db FROM PUBLIC");
                statement.execute("REVOKE CONNECT ON DATABASE bob_db FROM PUBLIC");
                statement.execute("GRANT CONNECT ON DATABASE alice_db TO alice");
                statement.execute("GRANT CONNECT ON DATABASE bob_db TO bob");
            }

            assertThat(canConnect(postgres, "alice_db", "alice", "alice-password")).isTrue();
            assertThat(canConnect(postgres, "bob_db", "bob", "bob-password")).isTrue();
            assertThatThrownBy(() -> canConnect(postgres, "bob_db", "alice", "alice-password"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> canConnect(postgres, "alice_db", "bob", "bob-password"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    private boolean canConnect(PostgreSQLContainer<?> postgres, String database, String user,
            String password) throws SQLException {
        String url = "jdbc:postgresql://" + postgres.getHost() + ":"
                + postgres.getMappedPort(5432) + "/" + database;
        try (Connection ignored = DriverManager.getConnection(url, user, password)) {
            return true;
        }
    }
}
