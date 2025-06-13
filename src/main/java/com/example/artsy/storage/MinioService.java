package com.example.artsy.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

@Data
public abstract class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String BUCKET_NAME;

    @Value("${minio.url}")
    private String url;

    private static final String SLASH = "/";
    private static final String PLAYLIST_FILE_CONTENT_TYPE = "application/vnd.apple.mpegurl";
    private static final String VIDEO_MP2T_CONTENT_TYPE = "video/MP2T";

    protected MinioService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Retryable(
            value = { IOException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
    protected void ensureBucketExists() throws Exception {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(BUCKET_NAME).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_NAME).build());
        }
    }

    @Recover
    public void recoverEnsureBucketExists(Exception e) throws Exception {
        throw new RuntimeException("Minio service is persistently unavailable, cannot ensure bucket existence.", e);
    }

    @Recover
    public String recoverGeneratePreSignedUploadUrl(RuntimeException e, String objectName, int expiryMinutes) {
        throw new RuntimeException("Minio service is currently unavailable for pre-signed URL generation.", e);
    }

    @Recover
    public String recoverUploadVideo(Exception e, MultipartFile file) {
        throw new RuntimeException("Minio service is currently unavailable for video upload.", e);
    }

    @Recover
    public InputStream recoverGetObjectStream(Exception e, String objectName) throws Exception {
        throw new RuntimeException("Minio service is currently unavailable for object retrieval.", e);
    }

    protected String objectKey(MultipartFile file, String chunkName) {
        return Objects.requireNonNull(file.getOriginalFilename()).replaceAll("\\.[^.]+$", "") + SLASH + chunkName;
    }
}
