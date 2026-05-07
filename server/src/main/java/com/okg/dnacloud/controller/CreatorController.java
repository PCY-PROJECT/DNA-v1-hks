package com.okg.dnacloud.controller;

import com.okg.dnacloud.entity.PackageVersionEntity;
import com.okg.dnacloud.entity.UploadSessionEntity;
import com.okg.dnacloud.service.creator.CreatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/creator")
@RequiredArgsConstructor
public class CreatorController {

    private final CreatorService creatorService;

    @PostMapping("/upload-session")
    public ResponseEntity<?> createUploadSession(@RequestBody Map<String, String> body) {
        log.info("[CreatorController.createUploadSession] start");
        String payoutAddress = body.get("payout_address");
        String packageHash = body.get("package_hash");
        if (payoutAddress == null || !payoutAddress.startsWith("0x")) {
            return ResponseEntity.badRequest().body(Map.of("error", "payout_address is required and must be 0x-prefixed"));
        }
        UploadSessionEntity session = creatorService.createUploadSession(payoutAddress, packageHash);
        return ResponseEntity.ok(Map.of(
            "upload_session_id", session.getId(),
            "nonce", session.getNonce(),
            "challenge", session.getChallenge(),
            "expires_at", session.getExpiresAt().toString()
        ));
    }

    @PostMapping("/packages/upload")
    public ResponseEntity<?> uploadPackage(
            @RequestParam("package") MultipartFile file,
            @RequestParam("upload_session_id") String uploadSessionId,
            @RequestParam("payout_signature") String payoutSignature,
            @RequestParam(value = "price", required = false) String price,
            @RequestParam(value = "currency", required = false) String currency,
            @RequestParam(value = "category", required = false) String category) {

        log.info("[CreatorController.uploadPackage] start, sessionId={}, fileName={}", uploadSessionId, file.getOriginalFilename());

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "package file is required"));
        }

        try {
            PackageVersionEntity entity = creatorService.uploadPackage(
                uploadSessionId, payoutSignature, file, price, currency, category);

            String marketplaceUrl = "dnacloud://package/" + entity.getPackageId();
            boolean rejected = entity.getStatus() == PackageVersionEntity.PackageStatus.rejected;

            return ResponseEntity.status(rejected ? 422 : 200).body(Map.of(
                "package_id", entity.getPackageId(),
                "version", entity.getVersion(),
                "status", entity.getStatus().name(),
                "validation_result", entity.getValidationResult() != null ? entity.getValidationResult() : "unknown",
                "marketplace_url", marketplaceUrl,
                "validation_report_url", "/v1/packages/" + entity.getPackageId() + "/" + entity.getVersion() + "/validation-report"
            ));
        } catch (IllegalArgumentException e) {
            log.error("[CreatorController.uploadPackage] bad request, error={}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[CreatorController.uploadPackage] failed, error={}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    @GetMapping("/packages")
    public ResponseEntity<?> getCreatorPackages(@RequestParam("wallet") String wallet) {
        log.info("[CreatorController.getCreatorPackages] wallet={}", wallet);
        return ResponseEntity.ok(Map.of("packages", creatorService.getCreatorPackages(wallet)));
    }

    @GetMapping("/earnings")
    public ResponseEntity<?> getEarnings(@RequestParam("wallet") String wallet) {
        log.info("[CreatorController.getEarnings] wallet={}", wallet);
        return ResponseEntity.ok(creatorService.getEarnings(wallet));
    }

    @GetMapping("/payouts")
    public ResponseEntity<?> getPayouts(@RequestParam("wallet") String wallet) {
        log.info("[CreatorController.getPayouts] wallet={}", wallet);
        return ResponseEntity.ok(creatorService.getPayouts(wallet));
    }

    @PostMapping("/admin/payouts/run-once")
    public ResponseEntity<?> runPayoutWorker() {
        log.info("[CreatorController.runPayoutWorker] triggered");
        return ResponseEntity.ok(creatorService.runPayoutWorkerOnce());
    }
}
