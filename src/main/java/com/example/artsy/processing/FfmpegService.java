package com.example.artsy.processing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.UUID;

@Slf4j
@Service
public class FfmpegService {

  public File transcodeToHLS(File inputFile) throws  IOException, InterruptedException {
    
    String outputDirName = "hls_output/" + UUID.randomUUID();
    File outputDir = new File(outputDirName);
    Process process = getProcess(inputFile, outputDir);

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        log.info(line);
      }
    }

    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new RuntimeException("Ffmpeg failed with exit code " + exitCode);
    }


    return outputDir;

  }

  private static Process getProcess(File inputFile, File outputDir) throws IOException {

    if(!outputDir.mkdir())
      throw new IOException("Failed to create directory");

    String outputPath = outputDir.getAbsolutePath() + "/playlist.m3u8";

    ProcessBuilder builder = new ProcessBuilder(
            "ffmpeg", "-i", inputFile.getAbsolutePath(),
            "-codec", "copy", "-start_number", "0",
            "-hls_time", "10", "-hls_list_size", "0",
            "-f", "hls", outputPath
    );

    builder.redirectErrorStream(true);

    return builder.start();

  }

}
