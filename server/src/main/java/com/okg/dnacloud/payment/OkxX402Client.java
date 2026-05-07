package com.okg.dnacloud.payment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * OKX x402 payment client — local HMAC verification.
 *
 * The CLI signs the payment challenge using the user's OKX API credentials.
 * The server holds the same credentials and verifies the HMAC locally,
 * eliminating the need for a third-party OKX facilitator endpoint.
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    public X402VerifyResult verify(String paymentCredential, String resource, String expectedAmount, String expectedCurrency) {
        log.info("[OkxX402Client.verify] start, resource={}, expectedAmount={}", resource, expectedAmount);

        if (secretKey == null || secretKey.isBlank()) {
            log.error("[OkxX402Client.verify] OKX secret key not configured");
            return X402VerifyResult.builder().valid(false)
                    .errorMessage("OKX_SECRET_KEY not configured").build();
        }

        try {
            String credJson = new String(Base64.getDecoder().decode(paymentCredential), StandardCharsets.UTF_8);
            Map<String, Object> cred = objectMapper.readValue(credJson, new TypeReference<>() {});

            String credApiKey   = (String) cred.get("apiKey");
            String credPassphrase = (String) cred.get("passphrase");
            String credTimestamp  = (String) cred.get("timestamp");
            String credSignature  = (String) cred.get("signature");
            String credSignedBody = (String) cred.get("signedBody");
            String credResource   = (String) cred.get("resource");
            String credAmount     = (String) cred.get("amount");
            String credCurrency   = (String) cred.get("currency");
            String credNetwork    = (String) cred.get("network");
            String credNonce      = (String) cred.get("nonce");

            if (credApiKey == null || credTimestamp == null || credSignature == null
                    || credSignedBody == null || credResource == null) {
                return X402VerifyResult.builder().valid(false)
                        .errorMessage("Malformed payment credential").build();
            }

            if (!apiKey.equals(credApiKey)) {
                log.error("[OkxX402Client.verify] API key mismatch");
                return X402VerifyResult.builder().valid(false)
                        .errorMessage("API key mismatch").build();
            }

            Instant credTime = Instant.parse(credTimestamp);
            long ageSeconds = Math.abs(Instant.now().getEpochSecond() - credTime.getEpochSecond());
            if (ageSeconds > 300) {
                log.error("[OkxX402Client.verify] credential expired, ageSeconds={}", ageSeconds);
                return X402VerifyResult.builder().valid(false)
                        .errorMessage("Payment credential expired").build();
            }

            String expected = buildSignature(credTimestamp, "POST", credResource, credSignedBody);
            if (!expected.equals(credSignature)) {
                log.error("[OkxX402Client.verify] HMAC signature mismatch");
                return X402VerifyResult.builder().valid(false)
                        .errorMessage("HMAC signature verification failed").build();
            }

            String txHash = "okx-x402-" + credNonce;
            log.info("[OkxX402Client.verify] end, signature OK, txHash={}", txHash);

            return X402VerifyResult.builder()
                    .valid(true)
                    .txHash(txHash)
                    .payer(credApiKey)
                    .amount(credAmount)
                    .currency(credCurrency)
                    .network(credNetwork)
                    .build();

        } catch (Exception e) {
            log.error("[OkxX402Client.verify] failed, error={}", e.getMessage(), e);
            return X402VerifyResult.builder().valid(false).errorMessage(e.getMessage()).build();
        }
    }

    public String settle(String paymentCredential, String txHash) {
        log.info("[OkxX402Client.settle] start, txHash={}", txHash);
        String ref = "okx-settled-" + txHash + "-" + Instant.now().getEpochSecond();
        log.info("[OkxX402Client.settle] end, settlementRef={}", ref);
        return ref;
    }

    private String buildSignature(String timestamp, String method, String path, String body) {
        try {
            String message = timestamp + method + path + body;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to build HMAC signature", e);
        }
    }
}
