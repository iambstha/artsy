package com.example.artsy.repository;

import com.example.artsy.entity.BackupSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackupSessionRepository extends JpaRepository<BackupSession, Long> {
}
