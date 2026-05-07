package com.okg.dnacloud.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OkxX402Client {

    @Value("${okx.x402.verify-url}")
    private String verifyUrl;

    @Value("${okx.x402.settle-url}")
    private String settleUrl;

    @Value("${okx.x402.api-key}")
    private String apiKey;

    @Value("${okx.x402.secret-key}")
    private String secretKey;

    @Value("${okx.x402.passphrase}")
    private String passphrase;

    private final WebClient.Builder webClientBuilder;

    public X402VerifyResult verify(String paymentCredential, String resource, String expectedAmount, String expectedCurrency) {
        log.info("[OkxX402Client.verify] start, resource={}, expectedAmount={}", resource, expectedAmount);

        if (apiKey == null || apiKey.isBlank()) {
            log.error("[OkxX402Client.verify] OKX API key not configured");
            throw new IllegalStateException("OKX x402 API key not configured. Set OKX_API_KEY environment variable.");
        }

        try {
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            String signature = buildSignature(timestamp, "POST", "/v1/x402/verify", paymentCredential);

            Map<String, Object> requestBody = Map.of(
                "paymentCredential", paymentCredential,
                "resource", resource,
                "expectedAmount", expectedAmount,
                "expectedCurrency", expectedCurrency
            );

            Map<?, ?> response = webClientBuilder.build()
                    .post()
                    .uri(verifyUrl)
                    .header("OK-ACCESS-KEY", apiKey)
                    .header("OK-ACCESS-SIGN", signature)
                    .header("OK-ACCESS-TIMESTAMP", timestamp)
                    .header("OK-ACCESS-PASSPHRASE", passphrase)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) {
                log.error("[OkxX402Client.verify] null response from OKX");
                return X402VerifyResult.builder().valid(false).errorMessage("null response from OKX").build();
            }

            boolean valid = Boolean.TRUE.equals(response.get("valid"));
            log.info("[OkxX402Client.verify] end, valid={}", valid);

            return X402VerifyResult.builder()
                    .valid(valid)
                    .txHash((String) response.get("txHash"))
                    .payer((String) response.get("payer"))
                    .amount((String) response.get("amount"))
                    .currency((String) response.get("currency"))
                    .network((String) response.get("network"))
                    .build();

        } catch (Exception e) {
            log.error("[OkxX402Client.verify] failed, error={}", e.getMessage(), e);
            return X402VerifyResult.builder().valid(false).errorMessage(e.getMessage()).build();
        }
    }

    public String settle(String paymentCredential, String txHash) {
        log.info("[OkxX402Client.settle] start, txHash={}", txHash);

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OKX x402 API key not configured.");
        }

        try {
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            String body = "{\"paymentCredential\":\"" + paymentCredential + "\",\"txHash\":\"" + txHash + "\"}";
            String signature = buildSignature(timestamp, "POST", "/v1/x402/settle", body);

            Map<?, ?> response = webClientBuilder.build()
                    .post()
                    .uri(settleUrl)
                    .header("OK-ACCESS-KEY", apiKey)
                    .header("OK-ACCESS-SIGN", signature)
                    .header("OK-ACCESS-TIMESTAMP", timestamp)
                    .header("OK-ACCESS-PASSPHRASE", passphrase)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(Map.of("paymentCredential", paymentCredential, "txHash", txHash))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String ref = response != null ? (String) response.get("settlementRef") : null;
            log.info("[OkxX402Client.settle] end, settlementRef={}", ref);
            return ref;

        } catch (Exception e) {
            log.error("[OkxX402Client.settle] failed, error={}", e.getMessage(), e);
            throw new RuntimeException("OKX x402 settle failed: " + e.getMessage(), e);
        }
    }

    private String buildSignature(String timestamp, String method, String path, String body) {
        try {
            String message = timestamp + method + path + body;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build OKX signature", e);
        }
    }
}
