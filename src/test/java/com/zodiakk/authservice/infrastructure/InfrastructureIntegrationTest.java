package com.zodiakk.authservice.infrastructure;

import com.zodiakk.authservice.common.AbstractIntegrationTest;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Test infrastructure")
class InfrastructureIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PostgreSQLContainer postgresContainer;

    @Autowired
    private KafkaContainer kafkaContainer;

    @Test
    @DisplayName("uses a PostgreSQL 17 container")
    void shouldRunPostgres17() {
        assertThat(postgresContainer.isRunning()).isTrue();
        assertThat(postgresContainer.getDockerImageName()).startsWith("postgres:17");

        String version = jdbcTemplate.queryForObject("select version()", String.class);
        assertThat(version).contains("PostgreSQL 17");

        assertThat(jdbcTemplate.queryForObject("select current_database()", String.class))
                .isEqualTo("karyera_auth_service_db");

        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'",
                String.class
        );

        assertThat(tables).contains(
                "users",
                "auth_credentials",
                "refresh_tokens",
                "outbox_events",
                "processed_events",
                "administration_lock"
        );
    }

    @Test
    @DisplayName("runs an Apache Kafka container that accepts a cluster description")
    void shouldRunKafka() throws Exception {
        assertThat(kafkaContainer.isRunning()).isTrue();
        assertThat(kafkaContainer.getDockerImageName()).startsWith("apache/kafka:4.3.1");
        assertThat(kafkaContainer.getBootstrapServers()).isNotBlank();

        try (AdminClient adminClient = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaContainer.getBootstrapServers()
        ))) {
            String clusterId = adminClient.describeCluster()
                    .clusterId()
                    .get(15, TimeUnit.SECONDS);

            assertThat(clusterId).isNotBlank();
        }
    }

    @Test
    @DisplayName("starts containers from the pinned images")
    void shouldStartContainers() {
        assertThat(postgresContainer.isRunning()).isTrue();
        assertThat(kafkaContainer.isRunning()).isTrue();

        assertThat(postgresContainer.getDockerImageName()).contains("postgres:17");
        assertThat(kafkaContainer.getDockerImageName()).contains("apache/kafka:4.3.1");
    }
}
