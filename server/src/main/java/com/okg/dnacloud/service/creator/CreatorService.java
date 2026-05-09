package com.okg.dnacloud.service.creator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.okg.dnacloud.entity.*;
import com.okg.dnacloud.entity.PackageVersionEntity.PackageStatus;
import com.okg.dnacloud.entity.RevenueEntryEntity.RevenueStatus;
import com.okg.dnacloud.model.ValidationReport;
import com.okg.dnacloud.repository.*;
import com.okg.dnacloud.service.DnaScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreatorService {

    private final PackageVersionRepository packageVersionRepo;
    private final PaymentReceiptRepository paymentReceiptRepo;
    private final RevenueEntryRepository revenueEntryRepo;
    private final PayoutBatchRepository payoutBatchRepo;
    private final UploadSessionRepository uploadSessionRepo;
    private final PackageValidatorService validator;
    private final ObjectMapper objectMapper;
    private final DnaScoreService dnaScoreService;

    @Value("${dnacloud.artifact-store:./artifacts}")
    private String artifactStore;

    @Value("${dnacloud.platform-fee-rate:0.20}")
    private double platformFeeRate;

    @Value("${dnacloud.signing-key:}")
    private String signingKey;

    @Value("${dnacloud.payment-address:}")
    private String platformPaymentAddress;

    @Value("${dnacloud.treasury-key:}")
    private String treasuryKey;

    // ==================== Upload Session ====================

    @Transactional
    public UploadSessionEntity createUploadSession(String payoutAddress, String packageHash) {
        log.info("[CreatorService.createUploadSession] start, payout={}", payoutAddress);
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String challenge = String.format("dnacloud-upload:%s:%s:%s:%s",
            nonce, packageHash != null ? packageHash : "pending", payoutAddress, Instant.now().getEpochSecond());

        UploadSessionEntity session = UploadSessionEntity.builder()
            .nonce(nonce)
            .payoutAddress(payoutAddress)
            .packageHash(packageHash)
            .challenge(challenge)
            .expiresAt(Instant.now().plusSeconds(600))
            .used(false)
            .createdAt(Instant.now())
            .build();

        UploadSessionEntity saved = uploadSessionRepo.save(session);
        log.info("[CreatorService.createUploadSession] end, sessionId={}", saved.getId());
        return saved;
    }

    // ==================== Package Upload ====================

    @Transactional
    public PackageVersionEntity uploadPackage(
            String uploadSessionId,
            String payoutSignature,
            MultipartFile file,
            String priceOverride,
            String currencyOverride,
            String categoryOverride
    ) throws Exception {
        log.info("[CreatorService.uploadPackage] start, sessionId={}", uploadSessionId);

        // Validate session
        UploadSessionEntity session = uploadSessionRepo.findByIdAndUsedFalse(uploadSessionId)
            .orElseThrow(() -> new IllegalArgumentException("Invalid or expired upload session"));
        if (session.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Upload session has expired");
        }

        // Save temp file
        Path tempFile = Files.createTempFile("dnacloud-upload-", ".zip");
        file.transferTo(tempFile.toFile());

        // Compute hash
        String packageHash = computeSha256(tempFile.toFile());

        // challenge 字段为预留设计，当前版本不验证签名（payout_signature 传 "none" 即可）
        // 未来版本可对 challenge 做钱包签名验证，以证明创作者持有 payout 地址

        // Mark session as used
        session.setUsed(true);
        uploadSessionRepo.save(session);

        // Run validation — fail fast, do NOT persist rejected packages
        ValidationReport report = validator.validateZip(tempFile.toFile());
        log.info("[CreatorService.uploadPackage] validation result={}, score={}", report.getResult(), report.getScore());
        if ("failed".equals(report.getResult())) {
            throw new com.okg.dnacloud.service.ValidationFailedException(report);
        }

        // Read manifest
        Map<String, Object> manifest = extractManifest(tempFile.toFile());
        String packageId = (String) manifest.get("id");
        String version = (String) manifest.get("version");
        String name = (String) manifest.getOrDefault("name", packageId);
        String category = categoryOverride != null ? categoryOverride : (String) manifest.getOrDefault("category", "general");

        if (packageId == null || version == null) {
            throw new IllegalArgumentException("manifest.json missing id or version");
        }

        // Check for duplicate version
        if (packageVersionRepo.existsByPackageIdAndVersion(packageId, version)) {
            throw new IllegalArgumentException("Package " + packageId + " version " + version + " already exists. Bump the version.");
        }

        // Price
        Map<String, Object> price = getNestedMap(manifest, "price");
        String priceAmount = priceOverride != null ? priceOverride : (String) price.getOrDefault("amount", "1.00");
        String priceCurrency = currencyOverride != null ? currencyOverride : (String) price.getOrDefault("currency", "USDT");
        String priceNetwork = (String) price.getOrDefault("network", "eip155:196");

        // Payout
        Map<String, Object> payout = getNestedMap(manifest, "payout");
        String payoutAddress = (String) payout.getOrDefault("address", session.getPayoutAddress());
        String payoutNetwork = (String) payout.getOrDefault("network", "eip155:196");
        String payoutCurrency = (String) payout.getOrDefault("currency", "USDT");

        // Creator
        Map<String, Object> creator = getNestedMap(manifest, "creator");
        String creatorWallet = (String) creator.getOrDefault("wallet_address", session.getPayoutAddress());
        String creatorDisplayName = (String) creator.get("display_name");

        // Store artifact
        PackageStatus status = report.getResult().equals("failed") ? PackageStatus.rejected : PackageStatus.published;
        String artifactPath = null;
        Instant publishedAt = null;

        if (status == PackageStatus.published) {
            artifactPath = storeArtifact(tempFile.toFile(), packageId, version, packageHash);
            publishedAt = Instant.now();
        }

        // Platform signature (simplified: hash of packageId+version+hash)
        String platformSignature = computePlatformSignature(packageId, version, packageHash);

        PackageVersionEntity entity = PackageVersionEntity.builder()
            .packageId(packageId)
            .version(version)
            .creatorWallet(creatorWallet.toLowerCase())
            .creatorDisplayName(creatorDisplayName)
            .name(name)
            .category(category)
            .description((String) manifest.get("description"))
            .packageHash(packageHash)
            .artifactPath(artifactPath)
            .manifestJson(objectMapper.writeValueAsString(manifest))
            .validationReportJson(objectMapper.writeValueAsString(report))
            .validationResult(report.getResult())
            .platformSignature(platformSignature)
            .priceAmount(priceAmount)
            .priceCurrency(priceCurrency)
            .priceNetwork(priceNetwork)
            .payoutAddress(payoutAddress.toLowerCase())
            .payoutNetwork(payoutNetwork)
            .payoutCurrency(payoutCurrency)
            .payoutSignature(payoutSignature)
            .riskLevel((String) manifest.getOrDefault("risk_level", "medium"))
            .status(status)
            .createdAt(Instant.now())
            .publishedAt(publishedAt)
            .build();

        PackageVersionEntity saved = packageVersionRepo.save(entity);

        // 自动评分（非阻塞，异常不影响主流程）
        try {
            int score = dnaScoreService.score(saved);
            saved.setDnaScore(score);
            // 从 manifest 读取 tags 字段
            Object tagsRaw = manifest.get("tags");
            if (tagsRaw instanceof List<?> tagList) {
                saved.setTags(tagList.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(",")));
            }
            packageVersionRepo.save(saved);
        } catch (Exception e) {
            log.warn("[CreatorService.uploadPackage] scoring non-fatal error: {}", e.getMessage());
        }

        Files.deleteIfExists(tempFile);

        log.info("[CreatorService.uploadPackage] end, packageId={}, version={}, status={}", packageId, version, status);
        return saved;
    }

    // ==================== Payment + Revenue ====================

    @Transactional
    public void recordPayment(
            String buyerAddress, String packageId, String packageVersion,
            String okxReceiptJson, String txHash, Long grossAmountMinimal
    ) {
        log.info("[CreatorService.recordPayment] start, packageId={}, version={}", packageId, packageVersion);

        PackageVersionEntity pkg = packageVersionRepo.findByPackageIdAndVersion(packageId, packageVersion)
            .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId + " " + packageVersion));

        PaymentReceiptEntity receipt = PaymentReceiptEntity.builder()
            .buyerAddress(buyerAddress)
            .packageId(packageId)
            .packageVersion(packageVersion)
            .creatorWallet(pkg.getCreatorWallet())
            .grossAmount(grossAmountMinimal)
            .currency(pkg.getPriceCurrency())
            .network(pkg.getPriceNetwork())
            .platformReceiverAddress(platformPaymentAddress)
            .okxReceiptJson(okxReceiptJson)
            .txHash(txHash)
            .status(PaymentReceiptEntity.PaymentStatus.settled)
            .createdAt(Instant.now())
            .build();

        PaymentReceiptEntity savedReceipt = paymentReceiptRepo.save(receipt);

        // Create revenue entry
        long fee = Math.round(grossAmountMinimal * platformFeeRate);
        long creatorAmount = grossAmountMinimal - fee;

        RevenueEntryEntity revenue = RevenueEntryEntity.builder()
            .paymentId(savedReceipt.getId())
            .packageId(packageId)
            .packageVersion(packageVersion)
            .creatorWallet(pkg.getCreatorWallet())
            .payoutAddress(pkg.getPayoutAddress())
            .network(pkg.getPayoutNetwork())
            .currency(pkg.getPayoutCurrency())
            .grossAmount(grossAmountMinimal)
            .platformFeeAmount(fee)
            .creatorAmount(creatorAmount)
            .status(RevenueStatus.pending_payout)
            .createdAt(Instant.now())
            .build();

        revenueEntryRepo.save(revenue);
        log.info("[CreatorService.recordPayment] end, receiptId={}, creatorAmount={}", savedReceipt.getId(), creatorAmount);
    }

    // ==================== Earnings / Payouts ====================

    public Map<String, Object> getEarnings(String walletAddress) {
        log.info("[CreatorService.getEarnings] start, wallet={}", walletAddress);
        List<RevenueEntryEntity> entries = revenueEntryRepo.findByCreatorWalletIgnoreCase(walletAddress);

        long totalGross = entries.stream().mapToLong(RevenueEntryEntity::getGrossAmount).sum();
        long totalFee = entries.stream().mapToLong(RevenueEntryEntity::getPlatformFeeAmount).sum();
        long pendingPayout = entries.stream()
            .filter(e -> e.getStatus() == RevenueStatus.pending_payout || e.getStatus() == RevenueStatus.payout_processing)
            .mapToLong(RevenueEntryEntity::getCreatorAmount).sum();
        long paidPayout = entries.stream()
            .filter(e -> e.getStatus() == RevenueStatus.paid)
            .mapToLong(RevenueEntryEntity::getCreatorAmount).sum();

        String currency = entries.isEmpty() ? "USDT" : entries.get(0).getCurrency();
        String network = entries.isEmpty() ? "eip155:196" : entries.get(0).getNetwork();
        String payoutAddr = entries.isEmpty() ? walletAddress : entries.get(0).getPayoutAddress();

        List<Map<String, Object>> entryList = entries.stream().map(e -> Map.<String, Object>of(
            "revenue_id", e.getId(),
            "payment_id", e.getPaymentId(),
            "package_id", e.getPackageId(),
            "package_version", e.getPackageVersion(),
            "gross_amount", String.valueOf(e.getGrossAmount()),
            "platform_fee_amount", String.valueOf(e.getPlatformFeeAmount()),
            "creator_amount", String.valueOf(e.getCreatorAmount()),
            "status", e.getStatus().name(),
            "created_at", e.getCreatedAt().toString()
        )).toList();

        log.info("[CreatorService.getEarnings] end, wallet={}, entries={}", walletAddress, entries.size());
        return Map.of(
            "total_gross", String.valueOf(totalGross),
            "platform_fee", String.valueOf(totalFee),
            "pending_payout", String.valueOf(pendingPayout),
            "paid_payout", String.valueOf(paidPayout),
            "currency", currency,
            "network", network,
            "payout_address", payoutAddr,
            "entries", entryList
        );
    }

    public Map<String, Object> getPayouts(String walletAddress) {
        log.info("[CreatorService.getPayouts] start, wallet={}", walletAddress);
        List<PayoutBatchEntity> batches = payoutBatchRepo.findByCreatorWalletIgnoreCase(walletAddress);
        List<Map<String, Object>> batchList = batches.stream().map(b -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", b.getId());
            map.put("total_amount", String.valueOf(b.getTotalAmount()));
            map.put("currency", b.getCurrency());
            map.put("network", b.getNetwork());
            map.put("status", b.getStatus().name());
            map.put("created_at", b.getCreatedAt().toString());
            if (b.getTxHash() != null) map.put("tx_hash", b.getTxHash());
            return map;
        }).toList();
        log.info("[CreatorService.getPayouts] end, wallet={}, batches={}", walletAddress, batches.size());
        return Map.of("batches", batchList);
    }

    public List<Map<String, Object>> getCreatorPackages(String walletAddress) {
        log.info("[CreatorService.getCreatorPackages] start, wallet={}", walletAddress);
        List<PackageVersionEntity> packages = packageVersionRepo.findByCreatorWalletIgnoreCase(walletAddress);
        List<Map<String, Object>> result = packages.stream().map(p -> Map.<String, Object>of(
            "id", p.getPackageId(),
            "name", p.getName(),
            "version", p.getVersion(),
            "status", p.getStatus().name(),
            "validation_result", p.getValidationResult() != null ? p.getValidationResult() : "unknown",
            "price", p.getPriceAmount(),
            "currency", p.getPriceCurrency()
        )).toList();
        log.info("[CreatorService.getCreatorPackages] end, wallet={}, count={}", walletAddress, result.size());
        return result;
    }

    // ==================== Payout Worker ====================

    @Transactional
    public Map<String, Object> runPayoutWorkerOnce() {
        log.info("[CreatorService.runPayoutWorkerOnce] start");
        List<RevenueEntryEntity> pending = revenueEntryRepo.findByStatus(RevenueStatus.pending_payout);
        if (pending.isEmpty()) {
            log.info("[CreatorService.runPayoutWorkerOnce] no pending entries");
            return Map.of("processed", 0, "message", "No pending payout entries");
        }

        // Group by creator + payout_address + currency + network
        Map<String, List<RevenueEntryEntity>> grouped = new LinkedHashMap<>();
        for (RevenueEntryEntity e : pending) {
            String key = e.getPayoutAddress() + "|" + e.getCurrency() + "|" + e.getNetwork();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }

        int batchCount = 0;
        for (Map.Entry<String, List<RevenueEntryEntity>> group : grouped.entrySet()) {
            List<RevenueEntryEntity> entries = group.getValue();
            long totalAmount = entries.stream().mapToLong(RevenueEntryEntity::getCreatorAmount).sum();
            String payoutAddress = entries.get(0).getPayoutAddress();
            String currency = entries.get(0).getCurrency();
            String network = entries.get(0).getNetwork();
            String creatorWallet = entries.get(0).getCreatorWallet();

            // Mark as processing
            entries.forEach(e -> e.setStatus(RevenueStatus.payout_processing));
            revenueEntryRepo.saveAll(entries);

            // Create payout batch
            PayoutBatchEntity batch = PayoutBatchEntity.builder()
                .creatorWallet(creatorWallet)
                .payoutAddress(payoutAddress)
                .currency(currency)
                .network(network)
                .totalAmount(totalAmount)
                .status(PayoutBatchEntity.PayoutStatus.pending)
                .createdAt(Instant.now())
                .build();
            PayoutBatchEntity savedBatch = payoutBatchRepo.save(batch);

            // Link entries to batch
            entries.forEach(e -> e.setPayoutBatchId(savedBatch.getId()));

            // Attempt on-chain transfer
            String txHash = attemptOnChainTransfer(payoutAddress, totalAmount, currency, network);
            if (txHash != null) {
                savedBatch.setTxHash(txHash);
                savedBatch.setStatus(PayoutBatchEntity.PayoutStatus.paid);
                savedBatch.setCompletedAt(Instant.now());
                entries.forEach(e -> e.setStatus(RevenueStatus.paid));
                log.info("[CreatorService.runPayoutWorkerOnce] batch paid, batchId={}, txHash={}", savedBatch.getId(), txHash);
            } else {
                savedBatch.setStatus(PayoutBatchEntity.PayoutStatus.payout_failed);
                entries.forEach(e -> e.setStatus(RevenueStatus.payout_failed));
                log.warn("[CreatorService.runPayoutWorkerOnce] payout failed, batchId={}, address={}", savedBatch.getId(), payoutAddress);
            }

            payoutBatchRepo.save(savedBatch);
            revenueEntryRepo.saveAll(entries);
            batchCount++;
        }

        log.info("[CreatorService.runPayoutWorkerOnce] end, batchCount={}", batchCount);
        return Map.of("processed", batchCount, "pending_entries", pending.size());
    }

    // ==================== Helpers ====================

    private String storeArtifact(File zipFile, String packageId, String version, String hash) throws Exception {
        Path dir = Paths.get(artifactStore, "packages", packageId, version);
        Files.createDirectories(dir);
        Path dest = dir.resolve(hash + ".zip");
        Files.copy(zipFile.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
        // Also write to the canonical download path expected by ArtifactService
        Path downloadDir = Paths.get(artifactStore, packageId, version);
        Files.createDirectories(downloadDir);
        Files.copy(zipFile.toPath(), downloadDir.resolve("package.zip"), StandardCopyOption.REPLACE_EXISTING);
        return dest.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractManifest(File zipFile) throws Exception {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.FileInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith("manifest.json") && !entry.getName().contains("..")) {
                    byte[] bytes = zis.readAllBytes();
                    return objectMapper.readValue(bytes, Map.class);
                }
                zis.closeEntry();
            }
        }
        throw new IllegalArgumentException("manifest.json not found in package");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getNestedMap(Map<String, Object> parent, String key) {
        Object val = parent.get(key);
        if (val instanceof Map) return (Map<String, Object>) val;
        return new HashMap<>();
    }

    private String computeSha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var is = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = is.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String computePlatformSignature(String packageId, String version, String hash) {
        try {
            String input = packageId + ":" + version + ":" + hash + ":" + signingKey;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return "sig:" + HexFormat.of().formatHex(md.digest(input.getBytes())).substring(0, 16);
        } catch (Exception e) {
            return "sig:unsigned";
        }
    }


    private String attemptOnChainTransfer(String toAddress, long amount, String currency, String network) {
        if (treasuryKey == null || treasuryKey.isBlank()) {
            log.warn("[CreatorService.attemptOnChainTransfer] DNACLOUD_TREASURY_KEY not configured, payout queued as pending, address={}", toAddress);
            return null;
        }
        // On-chain transfer not yet implemented. Configure DNACLOUD_TREASURY_KEY to enable.
        log.warn("[CreatorService.attemptOnChainTransfer] on-chain transfer not yet implemented, payout queued as pending, address={}", toAddress);
        return null;
    }
}
