package com.nextgen.optimizer.core;

/** Single source of truth for product naming, so a rebrand is a one-file change. */
public final class Brand {

    private Brand() {}

    public static final String NAME = "NextGen X";
    public static final String WORDMARK_PRIMARY = "NEXTGEN";
    public static final String WORDMARK_ACCENT = "X";
    public static final String TAGLINE = "Performance Suite";
    public static final String VERSION = "2.0.0";
    public static final String FULL_NAME = NAME + " " + TAGLINE;
}
