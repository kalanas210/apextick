package com.apextick.booking.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** S3-compatible storage (AWS S3, or MinIO locally via an endpoint override). */
public class S3TicketStorage implements TicketStorage {

    private static final Logger log = LoggerFactory.getLogger(S3TicketStorage.class);

    private final S3Client s3;
    private final String bucket;
    private final AtomicBoolean bucketChecked = new AtomicBoolean(false);

    public S3TicketStorage(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        ensureBucket();
        s3.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(content));
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(s3.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray());
        } catch (NoSuchKeyException | NoSuchBucketException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            // Storage is a cache of a regenerable artefact — degrade to a miss rather than failing the download.
            log.warn("S3 read failed for {} (treating as a miss)", key, e);
            return Optional.empty();
        }
    }

    /** Create the bucket on first write so a fresh MinIO volume needs no manual setup. */
    private void ensureBucket() {
        if (bucketChecked.get()) {
            return;
        }
        try {
            s3.headBucket(b -> b.bucket(bucket));
        } catch (NoSuchBucketException e) {
            log.info("Creating ticket bucket {}", bucket);
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception e) {
            // 404 arrives as a plain S3Exception from some S3-compatible servers
            if (e.statusCode() == 404) {
                log.info("Creating ticket bucket {}", bucket);
                s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            } else {
                throw e;
            }
        }
        bucketChecked.set(true);
    }
}
