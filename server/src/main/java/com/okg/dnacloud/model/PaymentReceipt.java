package com.okg.dnacloud.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentReceipt {
    private String txHash;
    private String payer;
    private String amount;
    private String currency;
    private String network;
    private String verifiedAt;
    private String settlementRef;
}
