package com.example.artsy.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "backup_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BackupSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant backupTimestamp;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "backupSession", cascade = CascadeType.ALL)
    private List<MediaFile> mediaFiles;
}
