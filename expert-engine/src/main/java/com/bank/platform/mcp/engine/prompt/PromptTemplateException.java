package com.bank.platform.mcp.engine.prompt;

/**
 * Thrown when a prompt template is malformed (unclosed tag, mismatched section).
 * Templates are checked-in build artifacts, so this is a programming/asset error
 * surfaced at compile/boot time, not a per-request failure.
 */
public class PromptTemplateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PromptTemplateException(String message) {
        super(message);
    }
}
