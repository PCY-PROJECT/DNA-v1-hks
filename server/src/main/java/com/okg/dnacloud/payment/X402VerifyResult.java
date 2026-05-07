package com.okg.dnacloud.payment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class X402VerifyResult {
    private boolean valid;
    private String txHash;
    private String payer;
    private String amount;
    private String currency;
    private String network;
    private String errorMessage;
}
