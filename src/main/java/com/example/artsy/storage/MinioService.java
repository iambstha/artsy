package com.example.artsy.storage;

import com.example.artsy.processing.FfmpegService;
import com.example.artsy.util.FileUtilService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

@Slf4j
@Data
public abstract class MinioService {

    private final MinioClient minioClient;
    private FfmpegService ffmpegService;
    private FileUtilService fileUtilService;


    @Value("${minio.bucket}")
    private String BUCKET_NAME;

    @Value("${minio.url}")
    private String url;

    private static final String SLASH = "/";
    private static final String PLAYLIST_FILE_CONTENT_TYPE = "application/vnd.apple.mpegurl";
    private static final String VIDEO_MP2T_CONTENT_TYPE = "video/MP2T";

    protected MinioService(MinioClient minioClient, FfmpegService ffmpegService, FileUtilService fileUtilService) {
        this.minioClient = minioClient;
        this.ffmpegService = ffmpegService;
        this.fileUtilService = fileUtilService;
    }

    public MinioService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    /**
     * Ensures that the configured Minio bucket exists. If not, it creates it.
     * This method is retryable in case of connection failures to Minio.
     *
     * @throws Exception If an error occurs while checking or creating the bucket after retries.
     */
    @Retryable(
            value = { IOException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
    protected void ensureBucketExists() throws Exception {
//        log.debug("Checking if bucket '{}' exists (attempt {}).", BUCKET_NAME, Objects.requireNonNull(RetrySynchronizationManager.getContext()).getRetryCount() + 1);
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(BUCKET_NAME).build())) {
                log.info("Bucket '{}' does not exist. Creating it now.", BUCKET_NAME);
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_NAME).build());
                log.info("Bucket '{}' created successfully.", BUCKET_NAME);
            } else {
                log.debug("Bucket '{}' already exists.", BUCKET_NAME);
            }
        } catch (IOException e) {
            log.warn("Minio connection or I/O failed during bucket existence check/creation. Retrying...", e);
            throw e;
        } catch (Exception e) {
            log.error("An unexpected error occurred during bucket operation for '{}'.", BUCKET_NAME, e);
            throw e;
        }
    }

    /**
     * Recovery method for ensureBucketExists.
     * This method is called if all retry attempts for ensureBucketExists fail.
     *
     * @param e The exception that caused all retries to fail.
     */
    @Recover
    public void recoverEnsureBucketExists(Exception e) throws Exception {
        log.error("All retry attempts failed for ensuring bucket '{}' exists. Minio seems persistently unavailable.", BUCKET_NAME, e);
        throw new RuntimeException("Minio service is persistently unavailable, cannot ensure bucket existence.", e);
    }


    /**
     * Recovery method for generatePreSignedUploadUrl.
     * This method is called if all retry attempts for generatePreSignedUploadUrl fail.
     *
     * @param e The exception that caused all retries to fail.
     * @param objectName The object name parameter from the original call.
     * @param expiryMinutes The expiry minutes parameter from the original call.
     * @return A fallback value or re-throw a specific exception.
     */
    @Recover
    public String recoverGeneratePreSignedUploadUrl(RuntimeException e, String objectName, int expiryMinutes) {
        log.error("All retry attempts failed for generating pre-signed URL for object: {}. Minio seems persistently unavailable.", objectName, e);
        throw new RuntimeException("Minio service is currently unavailable for pre-signed URL generation.", e);
    }

    /**
     * Recovery method for uploadVideo.
     * This method is called if all retry attempts for uploadVideo fail.
     *
     * @param e The exception that caused all retries to fail.
     * @param file The MultipartFile parameter from the original call.
     * @return A fallback value or re-throw a specific exception.
     */
    @Recover
    public String recoverUploadVideo(Exception e, MultipartFile file) {
        log.error("All retry attempts failed for uploading video file: {}. Minio seems persistently unavailable.", file.getOriginalFilename(), e);
        throw new RuntimeException("Minio service is currently unavailable for video upload.", e);
    }

    /**
     * Recovery method for getObjectStream.
     * This method is called if all retry attempts for getObjectStream fail.
     *
     * @param e The exception that caused all retries to fail.
     * @param objectName The object name parameter from the original call.
     * @return A fallback value (e.g., null) or re-throw a specific exception.
     */
    @Recover
    public InputStream recoverGetObjectStream(Exception e, String objectName) throws Exception {
        log.error("All retry attempts failed for retrieving object stream for: {}. Minio seems persistently unavailable.", objectName, e);
        // Depending on your application's logic, you might return null, an empty stream,
        // or re-throw a specific custom exception here. For now, re-throwing a specific runtime exception.
        throw new RuntimeException("Minio service is currently unavailable for object retrieval.", e);
    }

    /**
     * Constructs the object key for a given file and chunk name.
     * The object key typically includes the original filename (without extension) as a prefix.
     *
     * @param file      The original MultipartFile.
     * @param chunkName The name of the HLS chunk (e.g., "playlist.m3u8", "segment0001.ts").
     * @return The full object key for Minio.
     */
    protected String objectKey(MultipartFile file, String chunkName) {
        // Get the original filename and remove its extension to use as a directory prefix
        String baseName = Objects.requireNonNull(file.getOriginalFilename()).replaceAll("\\.[^.]+$", "");
        return baseName + SLASH + chunkName;
    }

    /**
     * Determines the content type of file based on its filename extension.
     *
     * @param filename The name of the file.
     * @return The appropriate MIME type string.
     */
    protected String getContentType(String filename) {
        // M3U8 files are playlists, TS files are video chunks
        return filename.endsWith(".m3u8") ? PLAYLIST_FILE_CONTENT_TYPE : VIDEO_MP2T_CONTENT_TYPE;
    }

}
