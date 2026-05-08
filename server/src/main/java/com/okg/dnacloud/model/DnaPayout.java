package com.okg.dnacloud.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DnaPayout {
    private String address;   // creator payout address
    private String currency;  // e.g. USDT
    private String network;   // e.g. eip155:196
    private String asset;     // USDT token contract address on the network
}
