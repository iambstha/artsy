package com.example.artsy.storage.impl;

import com.example.artsy.processing.PhotoProcessService;
import com.example.artsy.storage.MinioService;
import com.example.artsy.util.FileUtilService;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class PhotoStorageService extends MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String BUCKET_NAME;

    @Value("${minio.url}")
    private String url;

    private static final String SLASH = "/";

    public PhotoStorageService(MinioClient minioClient) {
        super(minioClient);
        this.minioClient = minioClient;
    }

    public String uploadPhoto(MultipartFile file) throws Exception {
        File tempFile = null;
        File hlsOutputDir = null;
        try {
            tempFile = FileUtilService.createTempFile(file);
            hlsOutputDir = PhotoProcessService.transcodePhoto(tempFile);
            ensureBucketExists();
            for (File chunk : Objects.requireNonNull(hlsOutputDir.listFiles())) {
                try (InputStream inputStream = new FileInputStream(chunk)) {
                    minioClient.putObject(
                            PutObjectArgs.builder()
                                    .bucket(BUCKET_NAME)
                                    .object(objectKey(file, chunk.getName()))
                                    .stream(inputStream, chunk.length(), -1)
                                    .contentType(FileUtilService.getContentType(chunk.getName()))
                                    .build()
                    );
                }
            }
            return makeStreamUrl(url, BUCKET_NAME, file);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw e;
        } finally {
            FileUtilService.deleteSafely(tempFile);
            FileUtilService.deleteDirectoryQuietly(hlsOutputDir);
        }
    }

    private String makeStreamUrl(String url, String bucketName, MultipartFile file) {
        String baseName = Objects.requireNonNull(file.getOriginalFilename()).replaceAll("\\.[^.]+$", "");
        return url + SLASH + bucketName + SLASH + baseName + SLASH + "playlist.m3u8";
    }

    @Retryable(
            value = { IOException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String getPreSignedUrl(String objectName, int expiryMinutes) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(BUCKET_NAME)
                            .object(objectName)
                            .expiry(expiryMinutes, TimeUnit.MINUTES)
                            .build()
            );
        } catch (IOException e) {
            throw new RuntimeException("Minio connection issue, triggering retry.", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate pre-signed URL", e);
        }
    }
}
