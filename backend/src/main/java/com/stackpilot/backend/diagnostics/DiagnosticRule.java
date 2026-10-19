package com.stackpilot.backend.diagnostics;

import com.stackpilot.backend.diagnostics.model.DiagnosticModels;

import java.util.List;

public interface DiagnosticRule {

    String id();

    List<DiagnosticModels.Finding> evaluate(DiagnosticContext context);
}
