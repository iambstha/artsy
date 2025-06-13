package com.example.artsy.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "media_files")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String originalFilename;

    private String storageKey; // key or path in MinIO

    private String mediaType; // image/jpeg, video/mp4, etc.

    private Long size; // size in bytes

    private Integer durationSeconds; // for videos, nullable for photos

    private String thumbnailStorageKey; // path for thumbnail in MinIO

    @ManyToOne
    @JoinColumn(name = "backup_session_id", nullable = false)
    private BackupSession backupSession;
}
