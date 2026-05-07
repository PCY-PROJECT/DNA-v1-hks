package com.okg.dnacloud.model;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationIssue {
    private String code;
    private String message;
    private String file;
    private String severity;
}
