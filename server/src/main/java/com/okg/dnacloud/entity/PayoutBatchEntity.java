package com.okg.dnacloud.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "payout_batches")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutBatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "creator_wallet", nullable = false)
    private String creatorWallet;

    @Column(name = "payout_address", nullable = false)
    private String payoutAddress;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private String network;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "tx_hash")
    private String txHash;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PayoutStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public enum PayoutStatus {
        pending, processing, paid, payout_failed
    }
}
