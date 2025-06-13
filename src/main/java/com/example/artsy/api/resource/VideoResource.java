package com.example.artsy.api.resource;

import com.example.artsy.storage.impl.PhotoStorageService;
import com.example.artsy.storage.impl.VideoServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * REST controller for handling video-related operations, including generating pre-signed URLs,
 * uploading videos, and streaming video chunks from Minio storage.
 */
@Slf4j // Enables Lombok's logging functionality
@RestController // Marks this class as a Spring REST controller, handling web requests
@RequestMapping("/videos") // Base path for all endpoints in this controller
@RequiredArgsConstructor // Lombok annotation to generate a constructor with required arguments (final fields)
public class VideoResource {

  private final VideoServiceImpl videoServiceImpl; // Injects the MinioService dependency

  // Constant for path separator
  private static final String SLASH = "/";
  private final PhotoStorageService photoStorageService;

  /**
   * Generates a pre-signed URL for a client to upload a video file directly to Minio.
   * This offloads the file upload from the backend service.
   *
   * @param fileName The desired name of the file in Minio storage.
   * @return A ResponseEntity containing the pre-signed URL string or an error message.
   */
  @GetMapping("/presigned-url")
  public ResponseEntity<String> getPreSignedUploadUrl(
          @RequestParam String fileName // Expects 'fileName' as a request parameter
  ) {
    try {
      // Define the object name within the Minio bucket, e.g., "uploads/myvideo.mp4"
      String objectName = "uploads/" + fileName;
      // Generate a pre-signed URL valid for 60 minutes for a PUT operation
      String url = videoServiceImpl.generatePreSignedUploadUrl(objectName, 60);
      log.info("Successfully generated pre-signed URL for object: {}", objectName);
      return ResponseEntity.ok(url); // Return the URL with HTTP 200 OK
    } catch (Exception e) {
      log.error("Failed to generate pre-signed URL for file: {}", fileName, e);
      // Return a 500 Internal Server Error with a descriptive message
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
              .body("Error generating pre-signed URL: " + e.getMessage());
    }
  }

  /**
   * Uploads a video file received as a MultipartFile. The service handles transcoding
   * the video to HLS format and storing chunks in Minio.
   *
   * @param file The MultipartFile containing the video data.
   * @return A ResponseEntity containing the streaming URL for the HLS playlist or an error message.
   */
  @PostMapping("/upload")
  public ResponseEntity<String> uploadVideo(
          @RequestParam("file") MultipartFile file // Expects 'file' as a request parameter
  ) {
    if (file.isEmpty()) {
      log.warn("Attempted to upload an empty file.");
      return ResponseEntity.badRequest().body("Upload failed: File is empty.");
    }
    try {
      // Upload the video via MinioService, which handles transcoding and chunking
      String streamUrl = videoServiceImpl.uploadVideo(file);
      log.info("Successfully uploaded video and generated stream URL: {}", streamUrl);
      return ResponseEntity.ok(streamUrl); // Return the streaming URL with HTTP 200 OK
    } catch (Exception e) {
      log.error("Video upload failed for file: {}", file.getOriginalFilename(), e);
      // Return a 500 Internal Server Error with a descriptive message
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Upload failed: " + e.getMessage());
    }
  }

  /**
   * Streams a specific video chunk (e.g., an HLS playlist or a TS segment) from Minio.
   * This endpoint is designed to serve video content for streaming clients.
   *
   * @param videoPrefix The directory-like prefix for the video (derived from original filename).
   * @param fileName    The name of the specific chunk file (e.g., "playlist.m3u8", "segment0001.ts").
   * @return A ResponseEntity containing the byte stream of the video chunk and appropriate headers.
   */
  @GetMapping("/stream/{videoPrefix}/{fileName}")
  public ResponseEntity<InputStreamResource> streamVideoChunk(
          @PathVariable String videoPrefix, // Extracts videoPrefix from the URL path
          @PathVariable String fileName // Extracts fileName from the URL path
  ) {
    String objectName = buildObjectName(videoPrefix, fileName);
    try {
      log.info("Attempting to stream video chunk: {}", objectName);
      InputStream stream = videoServiceImpl.getObjectStream(objectName);
      MediaType mediaType = determineMediaType(fileName);
      return buildStreamingResponse(stream, fileName, mediaType);
    } catch (FileNotFoundException e) {
      return handleStreamingFileNotFound(objectName, e);
    } catch (Exception e) {
      return handleStreamingException(objectName, e);
    }
  }

  /**
   * Constructs the full object name for Minio.
   * @param videoPrefix The prefix representing the video directory.
   * @param fileName The name of the chunk file.
   * @return The complete object name.
   */
  private String buildObjectName(String videoPrefix, String fileName) {
    return videoPrefix + SLASH + fileName;
  }

  /**
   * Determines the appropriate MediaType based on the file extension.
   * @param fileName The name of the file.
   * @return The determined MediaType.
   */
  private MediaType determineMediaType(String fileName) {
    if (fileName.endsWith(".m3u8")) {
      return MediaType.valueOf("application/vnd.apple.mpegurl"); // HLS playlist
    } else if (fileName.endsWith(".ts")) {
      return MediaType.valueOf("video/MP2T"); // MPEG-2 Transport Stream (HLS segment)
    } else {
      return MediaType.APPLICATION_OCTET_STREAM; // Fallback for unknown types
    }
  }

  /**
   * Builds the ResponseEntity for streaming the video chunk.
   * @param stream The InputStream of the video chunk.
   * @param fileName The name of the chunk file.
   * @param mediaType The MediaType for the response.
   * @return A ResponseEntity containing the InputStreamResource and appropriate headers.
   */
  private ResponseEntity<InputStreamResource> buildStreamingResponse(
          InputStream stream, String fileName, MediaType mediaType) {
    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline;filename=\"" + fileName + "\"")
            .header(HttpHeaders.CACHE_CONTROL, "max-age=3600, public")
            .contentType(mediaType)
            .body(new InputStreamResource(stream));
  }

  /**
   * Handles the case where a video chunk is not found.
   * @param objectName The name of the object that was not found.
   * @param e The FileNotFoundException.
   * @return A ResponseEntity with HttpStatus.NOT_FOUND.
   */
  private ResponseEntity<InputStreamResource> handleStreamingFileNotFound(
          String objectName, FileNotFoundException e) {
    log.warn("Video chunk not found: {}", objectName, e);
    return ResponseEntity.notFound().build();
  }

  /**
   * Handles general exceptions during video chunk streaming.
   * @param objectName The name of the object being streamed.
   * @param e The Exception that occurred.
   * @return A ResponseEntity with HttpStatus.INTERNAL_SERVER_ERROR.
   */
  private ResponseEntity<InputStreamResource> handleStreamingException(
          String objectName, Exception e) {
    log.error("Error streaming video chunk {}: ", objectName, e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
  }

  @PostMapping("/photos")
  public ResponseEntity<String> uploadPhoto(@RequestParam MultipartFile file) {
    String objectKey = photoStorageService.uploadPhoto(file);
    String url = photoStorageService.getPresignedUrl(objectKey, 60); // valid for 1 hour
    return ResponseEntity.ok(url);
  }


}
