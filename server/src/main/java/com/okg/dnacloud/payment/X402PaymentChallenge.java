package com.okg.dnacloud.payment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class X402PaymentChallenge {
    private String payTo;
    private String amount;
    private String currency;
    private String network;
    private String resource;
    private String nonce;
    private long expiresAt;
    private String scheme;
}
