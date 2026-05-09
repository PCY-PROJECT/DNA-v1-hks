package com.okg.dnacloud.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "package_versions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"package_id", "version"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackageVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "package_id", nullable = false)
    private String packageId;

    @Column(nullable = false)
    private String version;

    @Column(name = "creator_wallet", nullable = false)
    private String creatorWallet;

    @Column(name = "creator_display_name")
    private String creatorDisplayName;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    @Column(length = 2000)
    private String description;

    @Column(name = "package_hash", nullable = false)
    private String packageHash;

    @Column(name = "artifact_path", nullable = false)
    private String artifactPath;

    @Column(name = "manifest_json", columnDefinition = "TEXT")
    private String manifestJson;

    @Column(name = "validation_report_json", columnDefinition = "TEXT")
    private String validationReportJson;

    @Column(name = "validation_result")
    private String validationResult;

    @Column(name = "platform_signature")
    private String platformSignature;

    @Column(name = "price_amount", nullable = false)
    private String priceAmount;

    @Column(name = "price_currency", nullable = false)
    private String priceCurrency;

    @Column(name = "price_network", nullable = false)
    private String priceNetwork;

    @Column(name = "payout_address", nullable = false)
    private String payoutAddress;

    @Column(name = "payout_network", nullable = false)
    private String payoutNetwork;

    @Column(name = "payout_currency", nullable = false)
    private String payoutCurrency;

    @Column(name = "payout_signature")
    private String payoutSignature;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PackageStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "dna_score")
    private Integer dnaScore;   // 0-100 自动评分

    @Column(name = "tags", length = 500)
    private String tags;        // 英文关键词，逗号分隔，从 manifest 读取

    public enum PackageStatus {
        draft, uploaded, rejected, published, suspended, deprecated
    }
}
