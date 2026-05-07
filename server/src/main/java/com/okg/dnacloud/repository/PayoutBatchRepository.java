package com.okg.dnacloud.repository;

import com.okg.dnacloud.entity.PayoutBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayoutBatchRepository extends JpaRepository<PayoutBatchEntity, String> {
    List<PayoutBatchEntity> findByCreatorWalletIgnoreCase(String creatorWallet);
}
