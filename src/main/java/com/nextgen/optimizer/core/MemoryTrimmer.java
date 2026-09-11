package com.nextgen.optimizer.core;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

public class MemoryTrimmer {

    public interface Kernel32Mem extends Library {
        Kernel32Mem INSTANCE = Native.load("kernel32", Kernel32Mem.class);

        boolean SetProcessWorkingSetSize(Pointer hProcess, long dwMinimumWorkingSetSize, long dwMaximumWorkingSetSize);
        Pointer GetCurrentProcess();
    }

    /**
     * Trims the process physical RAM working set and releases unused pages back to the OS.
     */
    public static void trim() {
        try {
            Kernel32Mem.INSTANCE.SetProcessWorkingSetSize(
                    Kernel32Mem.INSTANCE.GetCurrentProcess(), -1, -1);
        } catch (Throwable ignored) {}
    }

    /**
     * Runs GC and immediately trims process physical RAM working set.
     */
    public static void gcAndTrim() {
        System.gc();
        trim();
    }
}
