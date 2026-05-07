package com.okg.dnacloud.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "revenue_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "payment_id", nullable = false)
    private String paymentId;

    @Column(name = "package_id", nullable = false)
    private String packageId;

    @Column(name = "package_version", nullable = false)
    private String packageVersion;

    @Column(name = "creator_wallet", nullable = false)
    private String creatorWallet;

    @Column(name = "payout_address", nullable = false)
    private String payoutAddress;

    @Column(name = "network", nullable = false)
    private String network;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "gross_amount", nullable = false)
    private Long grossAmount;

    @Column(name = "platform_fee_amount", nullable = false)
    private Long platformFeeAmount;

    @Column(name = "creator_amount", nullable = false)
    private Long creatorAmount;

    @Column(name = "payout_batch_id")
    private String payoutBatchId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RevenueStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public enum RevenueStatus {
        pending_payout, payout_processing, paid, payout_failed, held
    }
}
