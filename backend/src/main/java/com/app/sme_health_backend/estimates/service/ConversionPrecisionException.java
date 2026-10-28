package com.app.sme_health_backend.estimates.service;

import java.util.List;

/** The estimate is valid, but some values cannot be represented exactly by F05 project planning. */
public class ConversionPrecisionException extends EstimateException {
    private final List<ProjectConversionValidator.Issue> issues;
    public ConversionPrecisionException(List<ProjectConversionValidator.Issue> issues) {
        super(ProjectConversionValidator.CODE, 422,
                "This estimate is valid, but some values have more precision than Project Planning supports. Revise those values before conversion.");
        this.issues = List.copyOf(issues);
    }
    public List<ProjectConversionValidator.Issue> issues() { return issues; }
}
