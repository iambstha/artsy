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

    /**
     * Creates a temporary file from a MultipartFile.
     *
     * @param file The MultipartFile to convert to a temporary File.
     * @return A new temporary File containing the content of the MultipartFile.
     * @throws IOException If an I/O error occurs during file transfer.
     */
    public File createTempFile(MultipartFile file) throws IOException {
        // Create a temporary file with a prefix and the original filename as suffix
        File tempFile = File.createTempFile("upload-", file.getOriginalFilename());
        // Transfer the content of the MultipartFile to the temporary file
        file.transferTo(tempFile);
        log.info("Created temporary file: {}", tempFile.getAbsolutePath());
        return tempFile;
    }

    /**
     * Safely deletes a file. Logs a warning if the file exists but cannot be deleted.
     *
     * @param file The File to be deleted.
     */
    public void deleteSafely(File file) {
        if (file != null && file.exists()) {
            if (!file.delete()) {
                log.warn("Failed to delete temp file {}", file.getAbsolutePath());
            } else {
                log.info("Successfully deleted file: {}", file.getAbsolutePath());
            }
        }
    }

    /**
     * Safely deletes a directory and its contents recursively. This method will not throw an exception.
     *
     * @param directory The directory to be deleted.
     */
    public void deleteDirectoryQuietly(File directory) {
        if (directory != null && directory.exists() && directory.isDirectory()) {
            try {
                FileUtils.deleteDirectory(directory);
                log.info("Successfully deleted directory: {}", directory.getAbsolutePath());
            } catch (IOException e) {
                log.warn("Failed to delete directory {}: {}", directory.getAbsolutePath(), e.getMessage());
            }
        }
    }
}