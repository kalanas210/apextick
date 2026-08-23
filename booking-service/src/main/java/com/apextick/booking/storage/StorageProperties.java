package com.apextick.booking.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.storage.*}. With {@code s3.enabled=false} (the default) ticket PDFs are
 * held in memory, so the service runs with no object store at all.
 */
@ConfigurationProperties("app.storage")
public record StorageProperties(S3 s3) {

    /**
     * @param endpoint  override for S3-compatible servers (MinIO); blank means real AWS
     * @param pathStyle MinIO needs path-style addressing; AWS prefers virtual-host style
     */
    public record S3(boolean enabled, String bucket, String region, String endpoint,
                     String accessKey, String secretKey, boolean pathStyle) {
    }
}
