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


@Slf4j
@RestController
@RequestMapping("/videos")
@RequiredArgsConstructor
public class VideoResource {

  private final VideoServiceImpl videoServiceImpl;

  private static final String SLASH = "/";
  private final PhotoStorageService photoStorageService;

  @GetMapping("/presigned-url")
  public ResponseEntity<String> getPreSignedUploadUrl(
          @RequestParam String fileName
  ) {
    try {
      String objectName = "uploads/" + fileName;
      String url = videoServiceImpl.generatePreSignedUploadUrl(objectName, 60);
      return ResponseEntity.ok(url); // Return the URL with HTTP 200 OK
    } catch (Exception e) {
      log.error("Failed to generate pre-signed URL for file: {}", fileName, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
              .body("Error generating pre-signed URL: " + e.getMessage());
    }
  }

  @PostMapping("/upload")
  public ResponseEntity<String> uploadVideo(
          @RequestParam("file") MultipartFile file
  ) {
    if (file.isEmpty()) {
      return ResponseEntity.badRequest().body("Upload failed: File is empty.");
    }
    try {
      String streamUrl = videoServiceImpl.uploadVideo(file);
      return ResponseEntity.ok(streamUrl);
    } catch (Exception e) {
      log.error("Video upload failed for file: {}", file.getOriginalFilename(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Upload failed: " + e.getMessage());
    }
  }

  @GetMapping("/stream/{videoPrefix}/{fileName}")
  public ResponseEntity<InputStreamResource> streamVideoChunk(
          @PathVariable String videoPrefix,
          @PathVariable String fileName
  ) {
    String objectName = buildObjectName(videoPrefix, fileName);
    try {
      InputStream stream = videoServiceImpl.getObjectStream(objectName);
      MediaType mediaType = determineMediaType(fileName);
      return buildStreamingResponse(stream, fileName, mediaType);
    } catch (FileNotFoundException e) {
      return handleStreamingFileNotFound(objectName, e);
    } catch (Exception e) {
      return handleStreamingException(objectName, e);
    }
  }

  private String buildObjectName(String videoPrefix, String fileName) {
    return videoPrefix + SLASH + fileName;
  }

  private MediaType determineMediaType(String fileName) {
    if (fileName.endsWith(".m3u8")) {
      return MediaType.valueOf("application/vnd.apple.mpegurl");
    } else if (fileName.endsWith(".ts")) {
      return MediaType.valueOf("video/MP2T");
    } else {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
  }

  private ResponseEntity<InputStreamResource> buildStreamingResponse(
          InputStream stream, String fileName, MediaType mediaType) {
    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline;filename=\"" + fileName + "\"")
            .header(HttpHeaders.CACHE_CONTROL, "max-age=3600, public")
            .contentType(mediaType)
            .body(new InputStreamResource(stream));
  }

  private ResponseEntity<InputStreamResource> handleStreamingFileNotFound(
          String objectName, FileNotFoundException e) {
    log.warn("Video chunk not found: {}", objectName, e);
    return ResponseEntity.notFound().build();
  }

  private ResponseEntity<InputStreamResource> handleStreamingException(
          String objectName, Exception e) {
    log.error("Error streaming video chunk {}: ", objectName, e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
  }

  @PostMapping("/photos")
  public ResponseEntity<String> uploadPhoto(@RequestParam MultipartFile file) throws Exception {
    String objectKey = photoStorageService.uploadPhoto(file);
    String url = photoStorageService.getPreSignedUrl(objectKey, 60);
    return ResponseEntity.ok(url);
  }


}
