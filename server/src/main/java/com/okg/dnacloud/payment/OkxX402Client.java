package com.okg.dnacloud.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * OKX x402 payment client — calls OKX OnchainOS Facilitator REST API directly.
 *
 * Flow (initiated by OKX Payment Skill inside Claude Code):
 *   1. Buyer's Agentic Wallet detects HTTP 402.
 *   2. Payment Skill signs EIP-3009 TransferWithAuthorization.
 *   3. Skill retries request with X-PAYMENT header.
 *   4. This class calls OKX facilitator /verify to validate the signature.
 *   5. Then calls /settle to submit on-chain transfer.
 *   6. Returns real txHash from X Layer.
 *
 * Server prerequisites:
 *   - OKX_API_KEY / OKX_SECRET_KEY / OKX_PASSPHRASE (OnchainOS dev portal)
 *   - DNACLOUD_PAYMENT_ADDRESS (platform receive wallet)
 */
@Slf4j
@Component
public class OkxX402Client {

    @Value("${okx.x402.api-key:}")
    private String apiKey;

    @Value("${okx.x402.secret-key:}")
    private String secretKey;

    @Value("${okx.x402.passphrase:}")
    private String passphrase;

    @Value("${okx.x402.facilitator-url:https://www.okx.com/api/v5/onchainos/x402}")
    private String facilitatorBaseUrl;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && secretKey != null && !secretKey.isBlank()
                && passphrase != null && !passphrase.isBlank();
    }

    /**
     * Verify and settle a payment received via X-PAYMENT header.
     */
    public X402VerifyResult verifyAndSettle(
            String xPaymentHeader,
            String resource,
            String maxAmountRequired,
            String currency,
            String payTo,
            String asset,
            String network) {

        log.info("[OkxX402Client.verifyAndSettle] start, resource={}, amount={} {}", resource, maxAmountRequired, currency);

        if (!isConfigured()) {
            return X402VerifyResult.builder().valid(false)
                    .errorMessage("OKX x402 not configured — set OKX_API_KEY/SECRET/PASSPHRASE").build();
        }

        try {
            Map<String, Object> paymentRequirements = new HashMap<>();
            paymentRequirements.put("scheme", "exact");
            paymentRequirements.put("network", network);
            paymentRequirements.put("maxAmountRequired", maxAmountRequired);
            paymentRequirements.put("resource", resource);
            paymentRequirements.put("description", "");
            paymentRequirements.put("mimeType", "");
            paymentRequirements.put("payTo", payTo);
            paymentRequirements.put("maxTimeoutSeconds", 300);
            paymentRequirements.put("asset", asset);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("x402Version", 1);
            requestBody.put("paymentPayload", xPaymentHeader);
            requestBody.put("paymentRequirements", paymentRequirements);

            String bodyJson = objectMapper.writeValueAsString(requestBody);

            // ── Verify ────────────────────────────────────────────────────────
            String verifyUrl = facilitatorBaseUrl + "/verify";
            HttpResponse<String> verifyResp = postWithOkxAuth(verifyUrl, bodyJson);
            log.info("[OkxX402Client.verifyAndSettle] verify HTTP {}", verifyResp.statusCode());

            if (verifyResp.statusCode() != 200) {
                log.error("[OkxX402Client.verifyAndSettle] verify failed, HTTP {}: {}", verifyResp.statusCode(), verifyResp.body());
                return X402VerifyResult.builder().valid(false)
                        .errorMessage("OKX verify failed, HTTP " + verifyResp.statusCode()).build();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> verifyResult = objectMapper.readValue(verifyResp.body(), Map.class);
            boolean isValid = Boolean.TRUE.equals(verifyResult.get("isValid"));
            if (!isValid) {
                String reason = String.valueOf(verifyResult.getOrDefault("invalidReason", "unknown"));
                log.error("[OkxX402Client.verifyAndSettle] verify rejected: {}", reason);
                return X402VerifyResult.builder().valid(false).errorMessage(reason).build();
            }

            String payer = String.valueOf(verifyResult.getOrDefault("payer", ""));
            log.info("[OkxX402Client.verifyAndSettle] verify OK, payer={}, settling...", payer);

            // ── Settle ────────────────────────────────────────────────────────
            String settleUrl = facilitatorBaseUrl + "/settle";
            HttpResponse<String> settleResp = postWithOkxAuth(settleUrl, bodyJson);
            log.info("[OkxX402Client.verifyAndSettle] settle HTTP {}", settleResp.statusCode());

            if (settleResp.statusCode() != 200) {
                log.error("[OkxX402Client.verifyAndSettle] settle failed, HTTP {}: {}", settleResp.statusCode(), settleResp.body());
                return X402VerifyResult.builder().valid(false)
                        .errorMessage("OKX settle failed, HTTP " + settleResp.statusCode()).build();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> settleResult = objectMapper.readValue(settleResp.body(), Map.class);
            boolean success = Boolean.TRUE.equals(settleResult.get("success"));
            if (!success) {
                String err = String.valueOf(settleResult.getOrDefault("error", "unknown"));
                log.error("[OkxX402Client.verifyAndSettle] settle rejected: {}", err);
                return X402VerifyResult.builder().valid(false).errorMessage("Settlement failed: " + err).build();
            }

            String txHash = String.valueOf(settleResult.getOrDefault("txHash", ""));
            String networkId = String.valueOf(settleResult.getOrDefault("networkId", network));
            log.info("[OkxX402Client.verifyAndSettle] settled OK, txHash={}, payer={}", txHash, payer);

            return X402VerifyResult.builder()
                    .valid(true)
                    .txHash(txHash)
                    .payer(payer)
                    .amount(maxAmountRequired)
                    .currency(currency)
                    .network(networkId)
                    .build();

        } catch (Exception e) {
            log.error("[OkxX402Client.verifyAndSettle] unexpected error: {}", e.getMessage(), e);
            return X402VerifyResult.builder().valid(false)
                    .errorMessage("Payment processing error: " + e.getMessage()).build();
        }
    }

    private HttpResponse<String> postWithOkxAuth(String url, String body) throws Exception {
        URI uri = URI.create(url);
        String path = uri.getRawPath();
        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        String sign = buildSign(timestamp, "POST", path, body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json")
                .header("OK-ACCESS-KEY", apiKey)
                .header("OK-ACCESS-SIGN", sign)
                .header("OK-ACCESS-TIMESTAMP", timestamp)
                .header("OK-ACCESS-PASSPHRASE", passphrase)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String buildSign(String timestamp, String method, String path, String body) throws Exception {
        String message = timestamp + method + path + body;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }
}
