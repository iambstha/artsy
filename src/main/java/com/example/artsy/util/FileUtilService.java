package com.example.artsy.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@Slf4j
@Service
public class FileUtilService {

    private static final String PLAYLIST_FILE_CONTENT_TYPE = "application/vnd.apple.mpegurl";
    private static final String VIDEO_MP2T_CONTENT_TYPE = "video/MP2T";

    public static File createTempFile(MultipartFile file) throws IOException {
        File tempFile = File.createTempFile("upload-", file.getOriginalFilename());
        file.transferTo(tempFile);
        return tempFile;
    }

    public static void deleteSafely(File file) {
        if (file != null && file.exists()) {
            if (!file.delete()) {
                log.warn("Failed to delete temp file {}", file.getAbsolutePath());
            } else {
                log.info("Successfully deleted file: {}", file.getAbsolutePath());
            }
        }
    }

    public static void deleteDirectoryQuietly(File directory) {
        if (directory != null && directory.exists() && directory.isDirectory()) {
            try {
                FileUtils.deleteDirectory(directory);
                log.info("Successfully deleted directory: {}", directory.getAbsolutePath());
            } catch (IOException e) {
                log.warn("Failed to delete directory {}: {}", directory.getAbsolutePath(), e.getMessage());
            }
        }
    }

    public static String getContentType(String filename) {
        return filename.endsWith(".m3u8") ? PLAYLIST_FILE_CONTENT_TYPE : VIDEO_MP2T_CONTENT_TYPE;
    }

    public static String getPhotoContentType(String fileName) {
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".gif")) return "image/gif";
        return "application/octet-stream";
    }


}