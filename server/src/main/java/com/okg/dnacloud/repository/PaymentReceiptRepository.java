package com.okg.dnacloud.repository;

import com.okg.dnacloud.entity.PaymentReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentReceiptRepository extends JpaRepository<PaymentReceiptEntity, String> {
    List<PaymentReceiptEntity> findByCreatorWallet(String creatorWallet);
    List<PaymentReceiptEntity> findByPackageId(String packageId);
}
