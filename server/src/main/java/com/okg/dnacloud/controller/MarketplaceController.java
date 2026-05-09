package com.okg.dnacloud.controller;

import com.okg.dnacloud.model.ArtifactResponse;
import com.okg.dnacloud.model.DnaPackageInfo;
import com.okg.dnacloud.service.ArtifactService;
import com.okg.dnacloud.service.MarketplaceService;
import com.okg.dnacloud.service.PaymentRequiredException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/dna")
@RequiredArgsConstructor
public class MarketplaceController {

    private final MarketplaceService marketplaceService;
    private final ArtifactService artifactService;

    @Value("${dnacloud.artifact-store:./artifacts}")
    private String artifactStore;

    @Value("${dnacloud.payment-address:}")
    private String paymentAddress;

    @Value("${dnacloud.usdt-contract-address:${USDT_CONTRACT_ADDRESS:}}")
    private String usdtContractAddress;

    private static final java.util.regex.Pattern SAFE_ID = java.util.regex.Pattern.compile("^[a-z0-9][a-z0-9\\-]{0,63}$");
    private static final java.util.regex.Pattern SAFE_VER = java.util.regex.Pattern.compile("^\\d+\\.\\d+\\.\\d+([\\-+][a-zA-Z0-9.]+)?$");

    @GetMapping("/search")
    public ResponseEntity<List<DnaPackageInfo>> search(@RequestParam(defaultValue = "") String q) {
        log.info("[MarketplaceController.search] q={}", q);
        return ResponseEntity.ok(marketplaceService.search(q));
    }

    @GetMapping("/{packageId}")
    public ResponseEntity<DnaPackageInfo> getPackage(@PathVariable String packageId) {
        if (!SAFE_ID.matcher(packageId).matches()) {
            return ResponseEntity.badRequest().build();
        }
        log.info("[MarketplaceController.getPackage] packageId={}", packageId);
        DnaPackageInfo pkg = marketplaceService.getById(packageId);
        if (pkg == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(pkg);
    }

    @GetMapping("/{packageId}/versions/{version}/artifact")
    public ResponseEntity<?> getArtifact(
            @PathVariable String packageId,
            @PathVariable String version,
            @RequestHeader(value = "X-PAYMENT", required = false) String xPayment) {

        if (!SAFE_ID.matcher(packageId).matches() || !SAFE_VER.matcher(version).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid package id or version"));
        }

        log.info("[MarketplaceController.getArtifact] packageId={}, version={}, hasPayment={}", packageId, version, xPayment != null);

        try {
            ArtifactResponse artifact = artifactService.acquireWithPayment(packageId, version, xPayment);

            // Build X-PAYMENT-RESPONSE header (standard x402)
            String paymentResponseHeader = buildPaymentResponseHeader(artifact);
            return ResponseEntity.ok()
                    .header("X-PAYMENT-RESPONSE", paymentResponseHeader)
                    .body(artifact);

        } catch (PaymentRequiredException e) {
            // Return standard x402 402 response with X-PAYMENT-REQUIREMENT header
            String requirementHeader = buildPaymentRequirementHeader(e);
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .header("X-PAYMENT-REQUIREMENT", requirementHeader)
                    .body(Map.of(
                        "x402Version", 1,
                        "error", "X-PAYMENT header is required"
                    ));
        } catch (IllegalArgumentException e) {
            log.error("[MarketplaceController.getArtifact] bad request, error={}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("[MarketplaceController.getArtifact] payment failed, error={}", e.getMessage());
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(Map.of("error", "payment_failed", "message", e.getMessage()));
        }
    }

    @GetMapping("/{packageId}/versions/{version}/download")
    public ResponseEntity<FileSystemResource> downloadArtifact(
            @PathVariable String packageId,
            @PathVariable String version) {

        if (!SAFE_ID.matcher(packageId).matches() || !SAFE_VER.matcher(version).matches()) {
            return ResponseEntity.badRequest().build();
        }

        log.info("[MarketplaceController.downloadArtifact] packageId={}, version={}", packageId, version);

        // Resolve and verify path stays within artifact store (prevent traversal)
        Path base = Paths.get(artifactStore).toAbsolutePath().normalize();
        Path target = base.resolve(packageId).resolve(version).resolve("package.zip").normalize();
        if (!target.startsWith(base)) {
            log.error("[MarketplaceController.downloadArtifact] path traversal attempt, packageId={}, version={}", packageId, version);
            return ResponseEntity.badRequest().build();
        }

        log.info("[MarketplaceController.downloadArtifact] resolvedPath={}", target);
        File zipFile = target.toFile();
        if (!zipFile.exists()) {
            log.warn("[MarketplaceController.downloadArtifact] file not found at path={}", target);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + packageId + "-" + version + ".zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(zipFile));
    }

    /** Build the standard x402 X-PAYMENT-REQUIREMENT header value (base64 JSON). */
    private String buildPaymentRequirementHeader(PaymentRequiredException e) {
        DnaPackageInfo pkg = e.getPackageInfo();
        String resource = "/v1/dna/" + e.getPackageId() + "/versions/" + e.getVersion() + "/artifact";

        // Normalize network to CAIP-2 format required by OKX OnchainOS
        String rawNetwork = pkg.getPrice().getNetwork();
        String network402 = (rawNetwork == null || rawNetwork.isBlank() || rawNetwork.equalsIgnoreCase("xlayer"))
                ? "eip155:196" : rawNetwork;

        // Resolve asset: prefer payout.asset, fall back to platform USDT contract
        String asset402 = (pkg.getPayout() != null && pkg.getPayout().getAsset() != null
                && !pkg.getPayout().getAsset().isBlank())
                ? pkg.getPayout().getAsset() : usdtContractAddress;

        Map<String, Object> requirement = Map.of(
            "scheme",             "exact",
            "network",            network402,
            "maxAmountRequired",  toMinimalUnit(pkg.getPrice().getAmount()),
            "resource",           resource,
            "description",        pkg.getName() + " v" + pkg.getVersion(),
            "mimeType",           "application/zip",
            "payTo",              paymentAddress,
            "maxTimeoutSeconds",  300,
            "asset",              asset402,
            "extra",              Map.of(
                "name",    pkg.getPrice().getCurrency(),
                "version", "2"
            )
        );

        Map<String, Object> x402 = Map.of(
            "x402Version", 1,
            "accepts",     List.of(requirement),
            "error",       "X-PAYMENT header is required"
        );

        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            return Base64.getEncoder().encodeToString(om.writeValueAsBytes(x402));
        } catch (Exception ex) {
            log.error("[MarketplaceController.buildPaymentRequirementHeader] serialization failed", ex);
            return "";
        }
    }

    /** Build the standard x402 X-PAYMENT-RESPONSE header value (base64 JSON). */
    private String buildPaymentResponseHeader(ArtifactResponse artifact) {
        Map<String, Object> response = Map.of(
            "success",   true,
            "txHash",    artifact.getPaymentReceipt().getTxHash(),
            "network",   artifact.getPaymentReceipt().getNetwork(),
            "payer",     artifact.getPaymentReceipt().getPayer(),
            "amount",    artifact.getPaymentReceipt().getAmount(),
            "currency",  artifact.getPaymentReceipt().getCurrency()
        );
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            return Base64.getEncoder().encodeToString(om.writeValueAsBytes(response));
        } catch (Exception ex) {
            log.error("[MarketplaceController.buildPaymentResponseHeader] serialization failed", ex);
            return "";
        }
    }

    /** Convert human-readable amount (e.g. "0.001") to token minimal unit string (6 decimals). */
    private String toMinimalUnit(String amount) {
        try {
            double val = Double.parseDouble(amount);
            return String.valueOf((long)(val * 1_000_000));
        } catch (NumberFormatException ex) {
            return amount;
        }
    }
}
