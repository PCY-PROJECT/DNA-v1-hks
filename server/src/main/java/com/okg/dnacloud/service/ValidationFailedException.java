package com.okg.dnacloud.service;

import com.okg.dnacloud.model.ValidationReport;
import lombok.Getter;

@Getter
public class ValidationFailedException extends RuntimeException {

    private final ValidationReport validationReport;

    public ValidationFailedException(ValidationReport validationReport) {
        super("Package validation failed");
        this.validationReport = validationReport;
    }
}
