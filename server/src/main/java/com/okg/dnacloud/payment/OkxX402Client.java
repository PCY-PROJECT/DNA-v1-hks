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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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

    @Value("${okx.x402.facilitator-url:https://web3.okx.com/api/v6/pay/x402}")
    private String facilitatorBaseUrl;

    @Value("${dnacloud.base-url:http://localhost:8089}")
    private String baseUrl;

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
            // Decode X-PAYMENT header (Base64 JSON) from client
            @SuppressWarnings("unchecked")
            Map<String, Object> clientPayload = objectMapper.readValue(
                new String(Base64.getDecoder().decode(xPaymentHeader), StandardCharsets.UTF_8), Map.class);

            // Extract actual values from what the client signed
            @SuppressWarnings("unchecked")
            Map<String, Object> clientInnerPayload = (Map<String, Object>) clientPayload.getOrDefault("payload", new HashMap<>());
            @SuppressWarnings("unchecked")
            Map<String, Object> authorization = (Map<String, Object>) clientInnerPayload.getOrDefault("authorization", new HashMap<>());

            // Use the amount the client actually signed (minimal units, e.g. "1000" for 0.001 USDT)
            String signedAmount = String.valueOf(authorization.getOrDefault("value", maxAmountRequired));
            // Use the payTo the client actually signed for
            String signedPayTo = String.valueOf(authorization.getOrDefault("to", payTo));

            // extra: token name + EIP-3009 version
            Map<String, Object> extra = new HashMap<>();
            extra.put("name", currency);
            extra.put("version", "2");

            // paymentRequirements — matches official example structure
            Map<String, Object> paymentRequirements = new HashMap<>();
            paymentRequirements.put("scheme", "exact");
            paymentRequirements.put("network", network);
            paymentRequirements.put("amount", signedAmount);
            paymentRequirements.put("asset", asset);
            paymentRequirements.put("payTo", signedPayTo);
            paymentRequirements.put("maxTimeoutSeconds", 300);
            paymentRequirements.put("extra", extra);

            // paymentPayload.resource object
            Map<String, Object> resourceObj = new HashMap<>();
            resourceObj.put("url", baseUrl + resource);
            resourceObj.put("description", "DNAcloud artifact download");
            resourceObj.put("mimeType", "application/zip");

            // paymentPayload.accepted mirrors paymentRequirements
            Map<String, Object> accepted = new HashMap<>();
            accepted.put("scheme", "exact");
            accepted.put("network", network);
            accepted.put("amount", signedAmount);
            accepted.put("asset", asset);
            accepted.put("payTo", signedPayTo);
            accepted.put("maxTimeoutSeconds", 300);
            accepted.put("extra", extra);

            Map<String, Object> paymentPayloadObj = new HashMap<>();
            paymentPayloadObj.put("x402Version", 2);
            paymentPayloadObj.put("resource", resourceObj);
            paymentPayloadObj.put("accepted", accepted);
            paymentPayloadObj.put("payload", clientInnerPayload);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("x402Version", 2);
            requestBody.put("paymentPayload", paymentPayloadObj);
            requestBody.put("paymentRequirements", paymentRequirements);

            String bodyJson = objectMapper.writeValueAsString(requestBody);
            log.info("[OkxX402Client.verifyAndSettle] request body={}", bodyJson);

            // ── Verify ────────────────────────────────────────────────────────
            String verifyUrl = facilitatorBaseUrl + "/verify";
            HttpResponse<String> verifyResp = postWithOkxAuth(verifyUrl, bodyJson);
            log.info("[OkxX402Client.verifyAndSettle] verify HTTP {} body={}", verifyResp.statusCode(), verifyResp.body());

            if (verifyResp.statusCode() != 200) {
                log.error("[OkxX402Client.verifyAndSettle] verify failed, HTTP {}: {}", verifyResp.statusCode(), verifyResp.body());
                return X402VerifyResult.builder().valid(false)
                        .errorMessage("OKX verify failed, HTTP " + verifyResp.statusCode()).build();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> verifyResult = (Map<String, Object>)
                ((Map<String, Object>) objectMapper.readValue(verifyResp.body(), Map.class))
                .getOrDefault("data", new HashMap<>());
            boolean isValid = Boolean.TRUE.equals(verifyResult.get("isValid"));
            if (!isValid) {
                String reason = String.valueOf(verifyResult.getOrDefault("invalidReason", "unknown"));
                String message = String.valueOf(verifyResult.getOrDefault("invalidMessage", reason));
                log.error("[OkxX402Client.verifyAndSettle] verify rejected: {} — {}", reason, message);
                return X402VerifyResult.builder().valid(false).errorMessage(message).build();
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
            Map<String, Object> settleResult = (Map<String, Object>)
                ((Map<String, Object>) objectMapper.readValue(settleResp.body(), Map.class))
                .getOrDefault("data", new HashMap<>());
            boolean success = Boolean.TRUE.equals(settleResult.get("success"));
            if (!success) {
                String err = String.valueOf(settleResult.getOrDefault("error",
                    settleResult.getOrDefault("invalidMessage", "unknown")));
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
        // OKX requires millisecond-precision ISO-8601: "2026-05-09T10:30:00.123Z"
        String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now().truncatedTo(ChronoUnit.MILLIS));
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
