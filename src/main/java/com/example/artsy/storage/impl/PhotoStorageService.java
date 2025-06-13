package com.example.artsy.storage.impl;

import com.example.artsy.storage.MinioService;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class PhotoStorageService extends MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String BUCKET_NAME;

    @Value("${minio.url}")
    private String baseUrl;

    public PhotoStorageService(MinioClient minioClient) {
        super(minioClient);
        this.minioClient = minioClient;
    }

    /**
     * Upload a photo to MinIO and return the stored object key.
     */
    public String uploadPhoto(MultipartFile file) {
        String objectName = "photos/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

        try (InputStream is = file.getInputStream()) {
            // Ensure the bucket exists
            ensureBucketExists();

            // Upload the photo
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(objectName)
                            .stream(is, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            log.info("Uploaded photo to Minio with object name: {}", objectName);
            return objectName;

        } catch (Exception e) {
            log.error("Failed to upload photo to MinIO", e);
            throw new RuntimeException("Photo upload failed", e);
        }
    }

    /**
     * Generates a pre-signed URL for uploading an object to Minio.
     * The URL will expire after the specified number of minutes.
     * This method is retryable in case of connection failures to Minio.
     *
     * @param objectName    The name of the object to be uploaded.
     * @param expiryMinutes The expiry time for the URL in minutes.
     * @return A pre-signed URL string for PUT operation.
     * @throws RuntimeException If an error occurs during URL generation after retries.
     */
    @Retryable(
            value = { IOException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String getPresignedUrl(String objectName, int expiryMinutes) {
//        log.info("Attempting to generate pre-signed upload URL for object: {} (attempt {})", objectName, org.springframework.retry.support.RetrySynchronizationManager.getContext().getRetryCount() + 1);
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(BUCKET_NAME)
                            .object(objectName)
                            .expiry(expiryMinutes, TimeUnit.MINUTES)
                            .build()
            );
        } catch (IOException e) { // Catch IOException directly
            log.warn("Minio connection or I/O failed for pre-signed URL generation. Retrying...", e);
            throw new RuntimeException("Minio connection issue, triggering retry.", e);
        } catch (Exception e) {
            log.error("Failed to generate pre-signed URL for object: {}", objectName, e);
            throw new RuntimeException("Failed to generate pre-signed URL", e);
        }
    }

}
