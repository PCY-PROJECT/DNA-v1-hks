package com.okg.dnacloud.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DnaPrice {
    private String amount;
    private String currency;
    private String network;
}
