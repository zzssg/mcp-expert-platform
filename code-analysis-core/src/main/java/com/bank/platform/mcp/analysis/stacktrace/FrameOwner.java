package com.bank.platform.mcp.analysis.stacktrace;

/** Who owns a stack frame — used to find the true application root frame (Part 8.3). */
public enum FrameOwner { APPLICATION, FRAMEWORK, JDK, UNKNOWN }
