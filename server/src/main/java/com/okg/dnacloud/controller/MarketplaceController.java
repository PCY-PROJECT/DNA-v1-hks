package com.okg.dnacloud.controller;

import com.okg.dnacloud.model.ArtifactResponse;
import com.okg.dnacloud.model.DnaPackageInfo;
import com.okg.dnacloud.payment.X402PaymentChallenge;
import com.okg.dnacloud.service.ArtifactService;
import com.okg.dnacloud.service.MarketplaceService;
import com.okg.dnacloud.service.PaymentRequiredException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/search")
    public ResponseEntity<List<DnaPackageInfo>> search(@RequestParam(defaultValue = "") String q) {
        log.info("[MarketplaceController.search] q={}", q);
        return ResponseEntity.ok(marketplaceService.search(q));
    }

    @GetMapping("/{packageId}")
    public ResponseEntity<DnaPackageInfo> getPackage(@PathVariable String packageId) {
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

    private X402PaymentChallenge buildChallenge(PaymentRequiredException e) {
        return X402PaymentChallenge.builder()
                .payTo(System.getenv().getOrDefault("DNACLOUD_PAYMENT_ADDRESS", ""))
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
