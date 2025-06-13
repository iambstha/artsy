package com.example.artsy.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "video")
@Data
public class Video {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "title")
  private String title;

  @Column(name = "minio_path")
  private String minioPath;

  @Column(name = "uploaded_at")
  private LocalDateTime uploadedAt;
  
}
