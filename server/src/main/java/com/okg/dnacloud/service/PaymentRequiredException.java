package com.okg.dnacloud.service;

import com.okg.dnacloud.model.DnaPackageInfo;

public class PaymentRequiredException extends RuntimeException {
    private final String packageId;
    private final String version;
    private final DnaPackageInfo packageInfo;

    public PaymentRequiredException(String packageId, String version, DnaPackageInfo packageInfo) {
        super("Payment required for package: " + packageId);
        this.packageId = packageId;
        this.version = version;
        this.packageInfo = packageInfo;
    }

    public String getPackageId() { return packageId; }
    public String getVersion() { return version; }
    public DnaPackageInfo getPackageInfo() { return packageInfo; }
}
