package com.nextgen.optimizer.nativeapi;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

/**
 * Thin native bindings used by the optimizer. Every call is defensive: a missing
 * export, a denied privilege or an unexpected status returns a failure value
 * instead of throwing, so callers can degrade gracefully.
 */
public final class WinNative {

    private WinNative() {}

    // ── ntdll ────────────────────────────────────────────────────────────

    public interface NtDll extends StdCallLibrary {
        NtDll INSTANCE = Native.load("ntdll", NtDll.class);

        int NtSetSystemInformation(int infoClass, Pointer info, int length);

        int NtQuerySystemInformation(int infoClass, Pointer info, int length, IntByReference returnLength);
    }

    // ── powrprof ─────────────────────────────────────────────────────────

    public interface PowrProf extends StdCallLibrary {
        PowrProf INSTANCE = Native.load("powrprof", PowrProf.class);

        int PowerGetActiveScheme(Pointer userRootPowerKey, PointerByReference activePolicyGuid);

        int PowerSetActiveScheme(Pointer userRootPowerKey, Guid.GUID schemeGuid);

        int PowerReadACValueIndex(Pointer rootPowerKey, Guid.GUID schemeGuid, Guid.GUID subGroupGuid,
                                  Guid.GUID settingGuid, IntByReference acValueIndex);

        int PowerWriteACValueIndex(Pointer rootPowerKey, Guid.GUID schemeGuid, Guid.GUID subGroupGuid,
                                   Guid.GUID settingGuid, int acValueIndex);
    }

    // ── SYSTEM_INFORMATION_CLASS values ──────────────────────────────────

    public static final int SYSTEM_MEMORY_LIST_INFORMATION = 80;
    public static final int SYSTEM_COMBINE_PHYSICAL_MEMORY_INFORMATION = 130;

    // SYSTEM_MEMORY_LIST_COMMAND
    public static final int MEMORY_EMPTY_WORKING_SETS = 2;
    public static final int MEMORY_FLUSH_MODIFIED_LIST = 3;
    public static final int MEMORY_PURGE_STANDBY_LIST = 4;
    public static final int MEMORY_PURGE_LOW_PRIORITY_STANDBY_LIST = 5;

    private static volatile boolean privilegesEnabled;

    /**
     * Enables the privileges required to manage system memory lists. The
     * process runs elevated (see the manifest), so these are present in the
     * token but disabled by default. Safe to call repeatedly.
     */
    public static synchronized boolean enableMemoryPrivileges() {
        if (privilegesEnabled) return true;
        boolean profile = enablePrivilege("SeProfileSingleProcessPrivilege");
        boolean quota = enablePrivilege("SeIncreaseQuotaPrivilege");
        privilegesEnabled = profile && quota;
        return privilegesEnabled;
    }

    public static boolean enablePrivilege(String name) {
        WinNT.HANDLEByReference token = new WinNT.HANDLEByReference();
        try {
            if (!Advapi32.INSTANCE.OpenProcessToken(Kernel32.INSTANCE.GetCurrentProcess(),
                    WinNT.TOKEN_ADJUST_PRIVILEGES | WinNT.TOKEN_QUERY, token)) {
                return false;
            }
            WinNT.LUID luid = new WinNT.LUID();
            if (!Advapi32.INSTANCE.LookupPrivilegeValue(null, name, luid)) {
                return false;
            }
            WinNT.TOKEN_PRIVILEGES privileges = new WinNT.TOKEN_PRIVILEGES(1);
            privileges.Privileges[0] = new WinNT.LUID_AND_ATTRIBUTES(luid,
                    new WinDef.DWORD(WinNT.SE_PRIVILEGE_ENABLED));
            if (!Advapi32.INSTANCE.AdjustTokenPrivileges(token.getValue(), false, privileges, 0, null, null)) {
                return false;
            }
            // AdjustTokenPrivileges succeeds with ERROR_NOT_ALL_ASSIGNED when the token lacks the privilege.
            return Native.getLastError() == 0;
        } catch (Throwable t) {
            return false;
        } finally {
            if (token.getValue() != null) {
                Kernel32.INSTANCE.CloseHandle(token.getValue());
            }
        }
    }

    /** Sends a SYSTEM_MEMORY_LIST_COMMAND. Returns the NTSTATUS (0 = success). */
    public static int memoryListCommand(int command) {
        try {
            enableMemoryPrivileges();
            Memory buffer = new Memory(4);
            buffer.setInt(0, command);
            return NtDll.INSTANCE.NtSetSystemInformation(SYSTEM_MEMORY_LIST_INFORMATION, buffer, 4);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Merges identical physical pages (Windows 10+). Returns the number of pages
     * combined, or -1 when unsupported or denied.
     */
    public static long combineMemoryPages() {
        try {
            enableMemoryPrivileges();
            // MEMORY_COMBINE_INFORMATION_EX { HANDLE; ULONG_PTR PagesCombined; ULONG Flags; }
            int size = Native.POINTER_SIZE * 2 + 8;
            Memory buffer = new Memory(size);
            buffer.clear();
            int status = NtDll.INSTANCE.NtSetSystemInformation(SYSTEM_COMBINE_PHYSICAL_MEMORY_INFORMATION, buffer, size);
            if (status != 0) return -1;
            return Native.POINTER_SIZE == 8 ? buffer.getLong(8) : buffer.getInt(4);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Reads SYSTEM_MEMORY_LIST_INFORMATION. Returns page counts as
     * {zero, free, modified, modifiedNoWrite, bad, standby[0..7] summed, standbyLowPriority(0-2)},
     * or {@code null} when unavailable.
     */
    public static long[] queryMemoryLists() {
        try {
            enableMemoryPrivileges();
            int ptr = Native.POINTER_SIZE;
            int size = ptr * 22;
            Memory buffer = new Memory(size);
            IntByReference returned = new IntByReference();
            int status = NtDll.INSTANCE.NtQuerySystemInformation(SYSTEM_MEMORY_LIST_INFORMATION, buffer, size, returned);
            if (status != 0) return null;
            long[] fields = new long[22];
            for (int i = 0; i < 22; i++) {
                fields[i] = ptr == 8 ? buffer.getLong((long) i * ptr) : (buffer.getInt((long) i * ptr) & 0xFFFFFFFFL);
            }
            long standby = 0;
            long lowPriority = 0;
            for (int p = 0; p < 8; p++) {
                standby += fields[5 + p];
                if (p <= 2) lowPriority += fields[5 + p];
            }
            return new long[]{fields[0], fields[1], fields[2], fields[3], fields[4], standby, lowPriority};
        } catch (Throwable t) {
            return null;
        }
    }

    // ── Power schemes ────────────────────────────────────────────────────

    /** Returns the active power scheme GUID as "{...}", or null. */
    public static String activePowerScheme() {
        try {
            PointerByReference ref = new PointerByReference();
            if (PowrProf.INSTANCE.PowerGetActiveScheme(null, ref) != 0) return null;
            Pointer p = ref.getValue();
            Guid.GUID guid = new Guid.GUID(p);
            String text = guid.toGuidString();
            Kernel32.INSTANCE.LocalFree(p);
            return text;
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean setActivePowerScheme(String schemeGuid) {
        try {
            return PowrProf.INSTANCE.PowerSetActiveScheme(null, guid(schemeGuid)) == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Reads the AC value index of a power setting, or null when the setting is unknown. */
    public static Integer readAcValue(String scheme, String subGroup, String setting) {
        try {
            IntByReference value = new IntByReference();
            int rc = PowrProf.INSTANCE.PowerReadACValueIndex(null, guid(scheme), guid(subGroup), guid(setting), value);
            return rc == 0 ? value.getValue() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean writeAcValue(String scheme, String subGroup, String setting, int value) {
        try {
            int rc = PowrProf.INSTANCE.PowerWriteACValueIndex(null, guid(scheme), guid(subGroup), guid(setting), value);
            if (rc != 0) return false;
            String active = activePowerScheme();
            // Re-applying the active scheme makes the new index effective immediately.
            if (active != null && active.equalsIgnoreCase(normalizeGuid(scheme))) {
                setActivePowerScheme(active);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ── Process priority ─────────────────────────────────────────────────

    public static final int HIGH_PRIORITY_CLASS = 0x00000080;
    public static final int ABOVE_NORMAL_PRIORITY_CLASS = 0x00008000;
    public static final int NORMAL_PRIORITY_CLASS = 0x00000020;

    public static boolean setPriorityClass(long pid, int priorityClass) {
        WinNT.HANDLE handle = null;
        try {
            handle = Kernel32.INSTANCE.OpenProcess(0x0200 /* PROCESS_SET_INFORMATION */, false, (int) pid);
            if (handle == null) return false;
            return Kernel32Ext.INSTANCE.SetPriorityClass(handle, priorityClass);
        } catch (Throwable t) {
            return false;
        } finally {
            if (handle != null) Kernel32.INSTANCE.CloseHandle(handle);
        }
    }

    public interface Kernel32Ext extends StdCallLibrary {
        Kernel32Ext INSTANCE = Native.load("kernel32", Kernel32Ext.class);

        boolean SetPriorityClass(WinNT.HANDLE process, int priorityClass);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    public static String normalizeGuid(String guid) {
        if (guid == null) return null;
        String g = guid.trim();
        if (!g.startsWith("{")) g = "{" + g + "}";
        return g.toUpperCase();
    }

    private static Guid.GUID guid(String text) {
        return new Guid.GUID(normalizeGuid(text));
    }
}
