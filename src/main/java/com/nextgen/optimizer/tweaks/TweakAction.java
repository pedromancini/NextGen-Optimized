package com.nextgen.optimizer.tweaks;

import java.util.Map;

/**
 * One atomic, reversible change. Implementations must be idempotent:
 * applying twice is harmless and {@link #revert} restores exactly the state
 * captured by {@link #capture}.
 */
public interface TweakAction {

    /** False when the target does not exist on this PC (e.g. a service that is not installed). */
    default boolean isAvailable(TweakContext ctx) { return true; }

    boolean isApplied(TweakContext ctx);

    /** Records everything needed to undo {@link #apply}. Called before any write. */
    Map<String, String> capture(TweakContext ctx);

    boolean apply(TweakContext ctx);

    boolean revert(TweakContext ctx, Map<String, String> original);

    /** Short technical description shown in the details panel. */
    String describe();
}
