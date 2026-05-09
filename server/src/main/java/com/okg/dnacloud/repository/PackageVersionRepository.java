package com.okg.dnacloud.repository;

import com.okg.dnacloud.entity.PackageVersionEntity;
import com.okg.dnacloud.entity.PackageVersionEntity.PackageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PackageVersionRepository extends JpaRepository<PackageVersionEntity, String> {
    Optional<PackageVersionEntity> findByPackageIdAndVersion(String packageId, String version);
    List<PackageVersionEntity> findByStatus(PackageStatus status);
    List<PackageVersionEntity> findByCreatorWalletIgnoreCase(String creatorWallet);
    boolean existsByPackageIdAndVersion(String packageId, String version);
    List<PackageVersionEntity> findByStatusAndNameContainingIgnoreCaseOrStatusAndCategoryContainingIgnoreCase(
        PackageStatus status1, String name, PackageStatus status2, String category);
    List<PackageVersionEntity> findByDnaScoreIsNull();
}
