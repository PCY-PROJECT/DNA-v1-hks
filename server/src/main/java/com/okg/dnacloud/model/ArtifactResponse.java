package com.okg.dnacloud.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ArtifactResponse {
    private String packageId;
    private String version;
    private String downloadUrl;
    private String signature;
    private String sha256;
    private PaymentReceipt paymentReceipt;
}
