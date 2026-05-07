package com.okg.dnacloud.repository;

import com.okg.dnacloud.entity.RevenueEntryEntity;
import com.okg.dnacloud.entity.RevenueEntryEntity.RevenueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RevenueEntryRepository extends JpaRepository<RevenueEntryEntity, String> {
    List<RevenueEntryEntity> findByCreatorWalletIgnoreCase(String creatorWallet);
    List<RevenueEntryEntity> findByStatus(RevenueStatus status);
    List<RevenueEntryEntity> findByCreatorWalletIgnoreCaseAndStatus(String creatorWallet, RevenueStatus status);
    List<RevenueEntryEntity> findByPayoutBatchIdIsNull();
}
