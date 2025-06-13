package com.example.artsy.storage.impl;

import com.example.artsy.processing.FfmpegService;
import com.example.artsy.storage.MinioService;
import com.example.artsy.util.FileUtilService;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class VideoServiceImpl extends MinioService {

    private final MinioClient minioClient;
    private final FfmpegService ffmpegService;
    private final FileUtilService fileUtilService;

    @Value("${minio.bucket}")
    private String BUCKET_NAME;

    @Value("${minio.url}")
    private String url;

    private static final String SLASH = "/";

    public VideoServiceImpl(MinioClient minioClient, FfmpegService ffmpegService, FileUtilService fileUtilService) {
        super(minioClient, ffmpegService, fileUtilService);
        this.minioClient = minioClient;
        this.ffmpegService = ffmpegService;
        this.fileUtilService = fileUtilService;
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
    public String generatePreSignedUploadUrl(String objectName, int expiryMinutes) {
        log.info("Attempting to generate pre-signed upload URL for object: {} (attempt {})", objectName, RetrySynchronizationManager.getContext().getRetryCount() + 1);
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
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

//    /**
//     * Recovery method for generatePreSignedUploadUrl.
//     * This method is called if all retry attempts for generatePreSignedUploadUrl fail.
//     *
//     * @param e The exception that caused all retries to fail.
//     * @param objectName The object name parameter from the original call.
//     * @param expiryMinutes The expiry minutes parameter from the original call.
//     * @return A fallback value or re-throw a specific exception.
//     */
//    @Recover
//    public String recoverGeneratePreSignedUploadUrl(RuntimeException e, String objectName, int expiryMinutes) {
//        log.error("All retry attempts failed for generating pre-signed URL for object: {}. Minio seems persistently unavailable.", objectName, e);
//        throw new RuntimeException("Minio service is currently unavailable for pre-signed URL generation.", e);
//    }


    /**
     * Uploads a video file to Minio, first transcoding it to HLS format.
     * The original temporary file and the HLS output directory are cleaned up afterwards.
     * This method is retryable in case of connection failures to Minio.
     *
     * @param file The MultipartFile representing the video to upload.
     * @return The streaming URL for the uploaded HLS playlist.
     * @throws Exception If any error occurs during file processing, transcoding, or upload after retries.
     */
    @Retryable(
            value = { IOException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String uploadVideo(MultipartFile file) throws Exception {
//        log.info("Starting video upload process for file: {} (attempt {})", file.getOriginalFilename(), Objects.requireNonNull(RetrySynchronizationManager.getContext()).getRetryCount() + 1);
        File tempFile = null;
        File hlsOutputDir = null;
        try {
            // Create a temporary file from the MultipartFile using FileUtilService
            tempFile = fileUtilService.createTempFile(file);
            log.info("Video transcoded to HLS from temp file: {}", tempFile.getAbsolutePath());

            // Transcode the video to HLS format using FfmpegService
            hlsOutputDir = ffmpegService.transcodeToHLS(tempFile);
            log.info("Video transcoded to HLS output directory: {}", hlsOutputDir.getAbsolutePath());

            // Ensure the Minio bucket exists before uploading
            ensureBucketExists();

            // Iterate through each chunk (TS file, M3U8 playlist) in the HLS output directory
            for (File chunk : Objects.requireNonNull(hlsOutputDir.listFiles())) {
                try (InputStream is = new FileInputStream(chunk)) {
                    log.debug("Uploading chunk: {} to Minio bucket: {}", chunk.getName(), BUCKET_NAME);
                    minioClient.putObject(
                            PutObjectArgs.builder()
                                    .bucket(BUCKET_NAME)
                                    .object(objectKey(file, chunk.getName()))
                                    .stream(is, chunk.length(), -1)
                                    .contentType(getContentType(chunk.getName()))
                                    .build()
                    );
                }
            }
            log.info("All HLS chunks for {} uploaded successfully.", file.getOriginalFilename());
            // Return the full streaming URL for the main HLS playlist
            return makeStreamUrl(url, BUCKET_NAME, file);
        } catch (IOException e) { // Catch IOException directly
            log.warn("Minio connection or I/O failed during video upload. Retrying...", e);
            throw e;
        } catch (Exception e) {
            log.error("An unexpected error occurred during video upload for file: {}", file.getOriginalFilename(), e);
            throw e;
        } finally {
            // Ensure temporary files and directories are cleaned up
            fileUtilService.deleteSafely(tempFile);
            fileUtilService.deleteDirectoryQuietly(hlsOutputDir);
        }
    }

//    /**
//     * Recovery method for uploadVideo.
//     * This method is called if all retry attempts for uploadVideo fail.
//     *
//     * @param e The exception that caused all retries to fail.
//     * @param file The MultipartFile parameter from the original call.
//     * @return A fallback value or re-throw a specific exception.
//     */
//    @Recover
//    public String recoverUploadVideo(Exception e, MultipartFile file) {
//        log.error("All retry attempts failed for uploading video file: {}. Minio seems persistently unavailable.", file.getOriginalFilename(), e);
//        throw new RuntimeException("Minio service is currently unavailable for video upload.", e);
//    }

//    /**
//     * Ensures that the configured Minio bucket exists. If not, it creates it.
//     * This method is retryable in case of connection failures to Minio.
//     *
//     * @throws Exception If an error occurs while checking or creating the bucket after retries.
//     */
//    @Retryable(
//            value = { IOException.class },
//            maxAttempts = 3,
//            backoff = @Backoff(delay = 500, multiplier = 2)
//    )
//    private void ensureBucketExists() throws Exception {
////        log.debug("Checking if bucket '{}' exists (attempt {}).", BUCKET_NAME, Objects.requireNonNull(RetrySynchronizationManager.getContext()).getRetryCount() + 1);
//        try {
//            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(BUCKET_NAME).build())) {
//                log.info("Bucket '{}' does not exist. Creating it now.", BUCKET_NAME);
//                minioClient.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_NAME).build());
//                log.info("Bucket '{}' created successfully.", BUCKET_NAME);
//            } else {
//                log.debug("Bucket '{}' already exists.", BUCKET_NAME);
//            }
//        } catch (IOException e) {
//            log.warn("Minio connection or I/O failed during bucket existence check/creation. Retrying...", e);
//            throw e;
//        } catch (Exception e) {
//            log.error("An unexpected error occurred during bucket operation for '{}'.", BUCKET_NAME, e);
//            throw e;
//        }
//    }

//    /**
//     * Recovery method for ensureBucketExists.
//     * This method is called if all retry attempts for ensureBucketExists fail.
//     *
//     * @param e The exception that caused all retries to fail.
//     */
//    @Recover
//    public void recoverEnsureBucketExists(Exception e) throws Exception {
//        log.error("All retry attempts failed for ensuring bucket '{}' exists. Minio seems persistently unavailable.", BUCKET_NAME, e);
//        throw new RuntimeException("Minio service is persistently unavailable, cannot ensure bucket existence.", e);
//    }

    /**
     * Retrieves an object's content as an InputStream from Minio.
     * This method is retryable in case of connection failures to Minio.
     *
     * @param objectName The name of the object to retrieve.
     * @return An InputStream providing access to the object's content.
     * @throws Exception If an error occurs during object retrieval after retries.
     */
    @Retryable(
            value = { IOException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public InputStream getObjectStream(String objectName) throws Exception {
        log.info("Retrieving object stream for object: {} (attempt {})", objectName, Objects.requireNonNull(RetrySynchronizationManager.getContext()).getRetryCount() + 1);
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(objectName)
                            .build()
            );
        } catch (IOException e) {
            log.warn("Minio connection or I/O failed during object retrieval for '{}'. Retrying...", objectName, e);
            throw e;
        } catch (Exception e) {
            log.error("An unexpected error occurred during object retrieval for '{}'.", objectName, e);
            throw e;
        }
    }

//    /**
//     * Recovery method for getObjectStream.
//     * This method is called if all retry attempts for getObjectStream fail.
//     *
//     * @param e The exception that caused all retries to fail.
//     * @param objectName The object name parameter from the original call.
//     * @return A fallback value (e.g., null) or re-throw a specific exception.
//     */
//    @Recover
//    public InputStream recoverGetObjectStream(Exception e, String objectName) throws Exception {
//        log.error("All retry attempts failed for retrieving object stream for: {}. Minio seems persistently unavailable.", objectName, e);
//        // Depending on your application's logic, you might return null, an empty stream,
//        // or re-throw a specific custom exception here. For now, re-throwing a specific runtime exception.
//        throw new RuntimeException("Minio service is currently unavailable for object retrieval.", e);
//    }


//    /**
//     * Constructs the object key for a given file and chunk name.
//     * The object key typically includes the original filename (without extension) as a prefix.
//     *
//     * @param file      The original MultipartFile.
//     * @param chunkName The name of the HLS chunk (e.g., "playlist.m3u8", "segment0001.ts").
//     * @return The full object key for Minio.
//     */
//    private String objectKey(MultipartFile file, String chunkName) {
//        // Get the original filename and remove its extension to use as a directory prefix
//        String baseName = Objects.requireNonNull(file.getOriginalFilename()).replaceAll("\\.[^.]+$", "");
//        return baseName + SLASH + chunkName;
//    }

//    /**
//     * Determines the content type of a file based on its filename extension.
//     *
//     * @param filename The name of the file.
//     * @return The appropriate MIME type string.
//     */
//    private String getContentType(String filename) {
//        // M3U8 files are playlists, TS files are video chunks
//        return filename.endsWith(".m3u8") ? PLAYLIST_FILE_CONTENT_TYPE : VIDEO_MP2T_CONTENT_TYPE;
//    }

    /**
     * Constructs the full streaming URL for the main HLS playlist.
     *
     * @param baseUrl    The base URL of the Minio server.
     * @param bucketName The name of the Minio bucket.
     * @param file       The original MultipartFile (used to derive the base path for the playlist).
     * @return The complete URL to access the HLS playlist.
     */
    private String makeStreamUrl(String baseUrl, String bucketName, MultipartFile file) {
        // The playlist is expected to be at /bucket/original_filename_without_ext/playlist.m3u8
        String baseName = Objects.requireNonNull(file.getOriginalFilename()).replaceAll("\\.[^.]+$", "");
        return baseUrl + SLASH + bucketName + SLASH + baseName + SLASH + "playlist.m3u8";
    }

}
