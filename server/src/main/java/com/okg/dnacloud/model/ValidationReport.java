package com.okg.dnacloud.model;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationReport {
    private String result;
    private int score;
    private List<ValidationIssue> errors;
    private List<ValidationIssue> warnings;
    private int skillCount;
    private int agentCount;
    private int commandCount;
    private int mcpCount;
    private int hookCount;
}
