package com.okg.dnacloud.controller;

import com.okg.dnacloud.model.ArtifactResponse;
import com.okg.dnacloud.model.DnaPackageInfo;
import com.okg.dnacloud.payment.X402PaymentChallenge;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
            @RequestHeader(value = "X-Payment-Credential", required = false) String paymentCredential) {

        if (!SAFE_ID.matcher(packageId).matches() || !SAFE_VER.matcher(version).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid package id or version"));
        }

        log.info("[MarketplaceController.getArtifact] packageId={}, version={}, hasCredential={}", packageId, version, paymentCredential != null);

        try {
            ArtifactResponse artifact = artifactService.acquireWithPayment(packageId, version, paymentCredential);
            return ResponseEntity.ok(artifact);
        } catch (PaymentRequiredException e) {
            X402PaymentChallenge challenge = buildChallenge(e);
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .header("X-Payment-Scheme", "okx-x402")
                    .body(Map.of(
                        "error", "payment_required",
                        "message", "Payment required to download this DNA package",
                        "challenge", challenge
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

        File zipFile = target.toFile();
        if (!zipFile.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + packageId + "-" + version + ".zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(zipFile));
    }

    private X402PaymentChallenge buildChallenge(PaymentRequiredException e) {
        return X402PaymentChallenge.builder()
                .payTo(paymentAddress)
                .amount(e.getPackageInfo().getPrice().getAmount())
                .currency(e.getPackageInfo().getPrice().getCurrency())
                .network(e.getPackageInfo().getPrice().getNetwork())
                .resource("/v1/dna/" + e.getPackageId() + "/versions/" + e.getVersion() + "/artifact")
                .nonce(UUID.randomUUID().toString())
                .expiresAt(Instant.now().plusSeconds(300).getEpochSecond())
                .scheme("okx-x402")
                .build();
    }
}
