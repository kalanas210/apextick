package com.apextick.booking.storage;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the S3 adapter against a real S3 API (MinIO), including bucket bootstrap. */
@Testcontainers
class S3TicketStorageTest {

    private static final String BUCKET = "apextick-tickets";

    // MinIO withdrew its Docker Hub repository, so the same release is pulled from
    // quay.io; Testcontainers still has to be told it stands in for minio/minio.
    @Container
    static final MinIOContainer MINIO = new MinIOContainer(
            DockerImageName.parse("quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z")
                    .asCompatibleSubstituteFor("minio/minio"));

    private static S3TicketStorage storage() {
        StorageProperties props = new StorageProperties(new StorageProperties.S3(
                true, BUCKET, "us-east-1", MINIO.getS3URL(),
                MINIO.getUserName(), MINIO.getPassword(), true));
        return new S3TicketStorage(new StorageConfig().s3Client(props), BUCKET);
    }

    @Test
    void creates_the_bucket_on_first_write_and_reads_the_object_back() {
        S3TicketStorage storage = storage();
        byte[] pdf = "%PDF-1.4 fake ticket".getBytes(StandardCharsets.UTF_8);

        storage.put("tickets/1/first.pdf", pdf, "application/pdf");
        Optional<byte[]> loaded = storage.get("tickets/1/first.pdf");

        assertThat(loaded).isPresent();
        assertThat(loaded.get()).isEqualTo(pdf);
    }

    @Test
    void a_missing_key_reads_as_empty_rather_than_throwing() {
        assertThat(storage().get("tickets/1/does-not-exist.pdf")).isEmpty();
    }

    @Test
    void overwrites_an_existing_key() {
        S3TicketStorage storage = storage();
        storage.put("tickets/2/same.pdf", "v1".getBytes(StandardCharsets.UTF_8), "application/pdf");
        storage.put("tickets/2/same.pdf", "v2".getBytes(StandardCharsets.UTF_8), "application/pdf");

        assertThat(storage.get("tickets/2/same.pdf"))
                .get().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.BYTE_ARRAY)
                .isEqualTo("v2".getBytes(StandardCharsets.UTF_8));
    }
}
