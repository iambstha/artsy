package com.example.artsy.processing;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

@Slf4j
@Service
public class PhotoProcessService {

  public static File transcodePhoto(File inputFile) throws IOException {
    String outputDirName = "photo_output/" + UUID.randomUUID();
    File outputDir = new File(outputDirName);
    if (!outputDir.mkdirs()) {
      throw new IOException("Failed to create output directory: " + outputDir.getAbsolutePath());
    }

    File originalImage = new File(outputDir, "original_" + inputFile.getName());
    Files.copy(inputFile.toPath(), originalImage.toPath());

    File processedImage = new File(outputDir, "image.jpg");
    BufferedImage image = ImageIO.read(inputFile);
    Thumbnails.of(image)
            .size(800, 600)
            .outputQuality(0.85)
            .outputFormat("jpg")
            .toFile(processedImage);

    return outputDir;
  }
}
