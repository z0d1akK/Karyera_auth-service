package com.zodiakk.authservice.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String POSTGRES_IMAGE = "postgres:17-alpine";

    public static final String KAFKA_IMAGE = "apache/kafka:4.3.1";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse(POSTGRES_IMAGE))
            .withDatabaseName("karyera_auth_service_db")
            .withUsername("postgres")
            .withPassword("postgres");

    private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE));

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return POSTGRES;
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return KAFKA;
    }
}
