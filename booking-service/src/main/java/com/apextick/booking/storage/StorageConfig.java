package com.apextick.booking.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

/**
 * Picks the ticket store from configuration: S3/MinIO when {@code app.storage.s3.enabled=true},
 * otherwise an in-memory one. The two conditions are mutually exclusive, so exactly one bean exists.
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.storage.s3", name = "enabled", havingValue = "true")
    public S3Client s3Client(StorageProperties props) {
        StorageProperties.S3 cfg = props.s3();
        S3ClientBuilder builder = S3Client.builder()
                // the blocking client needs one HTTP implementation; the JDK one keeps the jar light
                .httpClient(UrlConnectionHttpClient.create())
                .region(Region.of(cfg.region() == null || cfg.region().isBlank() ? "us-east-1" : cfg.region()));

        if (cfg.accessKey() != null && !cfg.accessKey().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(cfg.accessKey(), cfg.secretKey())));
        } // else fall back to the default provider chain (IAM role, profile, env)

        if (cfg.endpoint() != null && !cfg.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(cfg.endpoint()));
        }
        builder.forcePathStyle(cfg.pathStyle());
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.storage.s3", name = "enabled", havingValue = "true")
    public TicketStorage s3TicketStorage(S3Client s3Client, StorageProperties props) {
        return new S3TicketStorage(s3Client, props.s3().bucket());
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.storage.s3", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    public TicketStorage inMemoryTicketStorage() {
        return new InMemoryTicketStorage();
    }
}
