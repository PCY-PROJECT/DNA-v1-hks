package com.okg.dnacloud.service;

import com.okg.dnacloud.model.ArtifactResponse;
import com.okg.dnacloud.model.DnaPackageInfo;
import com.okg.dnacloud.model.PaymentReceipt;
import com.okg.dnacloud.payment.OkxX402Client;
import com.okg.dnacloud.payment.X402VerifyResult;
import com.okg.dnacloud.service.creator.CreatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtifactService {

    private final MarketplaceService marketplaceService;
    private final OkxX402Client x402Client;
    private final CreatorService creatorService;

    @Value("${dnacloud.artifact-store}")
    private String artifactStore;

    @Value("${dnacloud.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${dnacloud.local-test-mode:false}")
    private boolean localTestMode;

    @Value("${dnacloud.payment-address:}")
    private String paymentAddress;

    public ArtifactResponse acquireWithPayment(String packageId, String version, String paymentCredential) {
        log.info("[ArtifactService.acquireWithPayment] start, packageId={}, version={}, localTestMode={}", packageId, version, localTestMode);

        DnaPackageInfo pkg = marketplaceService.getById(packageId);
        if (pkg == null) {
            throw new IllegalArgumentException("Package not found: " + packageId);
        }

        if (localTestMode) {
            log.info("[ArtifactService.acquireWithPayment] local-test-mode: skipping payment verification");
            String artifactPathTest = resolveArtifactPath(packageId, version);
            String sha256Test = computeSha256(artifactPathTest);
            String testTxHash = "local-test-" + System.currentTimeMillis();
            PaymentReceipt testReceipt = PaymentReceipt.builder()
                    .txHash(testTxHash)
                    .payer("local-test-buyer")
                    .amount(pkg.getPrice().getAmount())
                    .currency(pkg.getPrice().getCurrency())
                    .network(pkg.getPrice().getNetwork())
                    .verifiedAt(Instant.now().toString())
                    .settlementRef("local-test-settlement-" + testTxHash)
                    .build();
            try {
                long grossMinimal = parseAmountToMinimal(pkg.getPrice().getAmount());
                creatorService.recordPayment(
                    "local-test-buyer", packageId, version,
                    "{\"settlementRef\":\"local-test-settlement-" + testTxHash + "\"}", testTxHash, grossMinimal
                );
            } catch (Exception e) {
                log.warn("[ArtifactService.acquireWithPayment] local-test revenue record failed (non-fatal): {}", e.getMessage());
            }
            return ArtifactResponse.builder()
                    .packageId(packageId)
                    .version(version)
                    .downloadUrl(baseUrl + "/v1/dna/" + packageId + "/versions/" + version + "/download")
                    .signature(loadSignature(packageId, version))
                    .sha256(sha256Test)
                    .paymentReceipt(testReceipt)
                    .build();
        }

        if (paymentCredential == null || paymentCredential.isBlank()) {
            log.info("[ArtifactService.acquireWithPayment] no payment credential, returning 402");
            throw new PaymentRequiredException(packageId, version, pkg);
        }

        String resource = "/v1/dna/" + packageId + "/versions/" + version + "/artifact";

        String usdtContract = System.getenv().getOrDefault("USDT_CONTRACT_ADDRESS", "");
        String resolvedPayTo  = (pkg.getPayout() != null && pkg.getPayout().getAddress() != null
                && !pkg.getPayout().getAddress().isBlank()
                && !pkg.getPayout().getAddress().equals("0x0000000000000000000000000000000000000001"))
                ? pkg.getPayout().getAddress() : paymentAddress;
        String resolvedAsset  = (pkg.getPayout() != null && pkg.getPayout().getAsset() != null
                && !pkg.getPayout().getAsset().isBlank())
                ? pkg.getPayout().getAsset() : usdtContract;
        // Normalize network: "xlayer" → "eip155:196"
        String rawNetwork = pkg.getPrice().getNetwork();
        String resolvedNetwork = (rawNetwork == null || rawNetwork.isBlank() || rawNetwork.equalsIgnoreCase("xlayer"))
                ? "eip155:196" : rawNetwork;

        // Verify + Settle via OKX Facilitator SDK
        X402VerifyResult verifyResult = x402Client.verifyAndSettle(
            paymentCredential,
            resource,
            pkg.getPrice().getAmount(),
            pkg.getPrice().getCurrency(),
            resolvedPayTo,
            resolvedAsset,
            resolvedNetwork
        );

        if (!verifyResult.isValid()) {
            log.error("[ArtifactService.acquireWithPayment] payment verify/settle failed, error={}", verifyResult.getErrorMessage());
            throw new IllegalStateException("OKX x402 payment failed: " + verifyResult.getErrorMessage());
        }

        String settlementRef = "okx-settled-" + verifyResult.getTxHash();

        String artifactPath = resolveArtifactPath(packageId, version);
        String sha256 = computeSha256(artifactPath);
        String signature = loadSignature(packageId, version);

        PaymentReceipt receipt = PaymentReceipt.builder()
                .txHash(verifyResult.getTxHash())
                .payer(verifyResult.getPayer())
                .amount(verifyResult.getAmount())
                .currency(verifyResult.getCurrency())
                .network(verifyResult.getNetwork())
                .verifiedAt(Instant.now().toString())
                .settlementRef(settlementRef)
                .build();

        ArtifactResponse response = ArtifactResponse.builder()
                .packageId(packageId)
                .version(version)
                .downloadUrl(baseUrl + "/v1/dna/" + packageId + "/versions/" + version + "/download")
                .signature(signature)
                .sha256(sha256)
                .paymentReceipt(receipt)
                .build();

        // Record payment in ledger (best-effort, non-blocking)
        try {
            long grossMinimal = parseAmountToMinimal(verifyResult.getAmount());
            creatorService.recordPayment(
                verifyResult.getPayer(), packageId, version,
                "{\"settlementRef\":\"" + settlementRef + "\"}", verifyResult.getTxHash(), grossMinimal
            );
        } catch (Exception e) {
            log.error("[ArtifactService.acquireWithPayment] revenue ledger record failed (non-fatal), error={}", e.getMessage());
        }

        log.info("[ArtifactService.acquireWithPayment] end, packageId={}, txHash={}", packageId, verifyResult.getTxHash());
        return response;
    }

    private String resolveArtifactPath(String packageId, String version) {
        return artifactStore + "/" + packageId + "/" + version + "/package.zip";
    }

    private String computeSha256(String path) {
        try {
            java.io.File file = new java.io.File(path);
            if (!file.exists()) {
                log.warn("[ArtifactService.computeSha256] file not found: {}", path);
                return "sha256-unavailable";
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = fis.read(buf)) != -1) {
                    digest.update(buf, 0, n);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            log.error("[ArtifactService.computeSha256] failed, error={}", e.getMessage(), e);
            return "sha256-unavailable";
        }
    }

    private String loadSignature(String packageId, String version) {
        return "dnacloud-sig-" + packageId + "-" + version;
    }

    private long parseAmountToMinimal(String amount) {
        if (amount == null) return 0L;
        try {
            return (long) (Double.parseDouble(amount) * 1_000_000L);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
