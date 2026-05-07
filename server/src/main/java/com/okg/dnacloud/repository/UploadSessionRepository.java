package com.okg.dnacloud.repository;

import com.okg.dnacloud.entity.UploadSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UploadSessionRepository extends JpaRepository<UploadSessionEntity, String> {
    Optional<UploadSessionEntity> findByNonce(String nonce);
    Optional<UploadSessionEntity> findByIdAndUsedFalse(String id);
}
