package com.nextgen.optimizer.services;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinReg;

/**
 * Thin wrapper around JNA's {@link Advapi32Util} for reading / writing the
 * Windows Registry.  Every public method catches all exceptions and returns a
 * safe default so that callers never need try/catch blocks.
 */
public class RegistryService {

    // ── Read ────────────────────────────────────────────────────────────

    /**
     * Reads a DWORD (REG_DWORD) value from the registry.
     *
     * @return the integer value, or {@code -1} if the key/value doesn't exist
     *         or any error occurs.
     */
    public int getIntValue(WinReg.HKEY root, String keyPath, String valueName) {
        return getIntValue(root, keyPath, valueName, -1);
    }

    public int getIntValue(WinReg.HKEY root, String keyPath, String valueName, int defaultValue) {
        try {
            return Advapi32Util.registryGetIntValue(root, keyPath, valueName);
        } catch (Win32Exception e) {
            return defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public String getStringValue(WinReg.HKEY root, String keyPath, String valueName) {
        return getStringValue(root, keyPath, valueName, "");
    }

    public String getStringValue(WinReg.HKEY root, String keyPath, String valueName, String defaultValue) {
        try {
            return Advapi32Util.registryGetStringValue(root, keyPath, valueName);
        } catch (Win32Exception e) {
            return defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public String[] getSubKeys(WinReg.HKEY root, String keyPath) {
        try {
            return Advapi32Util.registryGetKeys(root, keyPath);
        } catch (Exception e) {
            return new String[0];
        }
    }

    public String[] getValueNames(WinReg.HKEY root, String keyPath) {
        try {
            return Advapi32Util.registryGetValues(root, keyPath).keySet().toArray(new String[0]);
        } catch (Exception e) {
            return new String[0];
        }
    }

    public byte[] getBinaryValue(WinReg.HKEY root, String keyPath, String valueName) {
        try {
            return Advapi32Util.registryGetBinaryValue(root, keyPath, valueName);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Reads a value of any supported type: {@link Integer} (DWORD), {@link Long}
     * (QWORD), {@link String} (SZ / EXPAND_SZ), {@code byte[]} or {@code String[]}.
     *
     * @return the value, or {@code null} when it does not exist.
     */
    public Object getRawValue(WinReg.HKEY root, String keyPath, String valueName) {
        try {
            if (!Advapi32Util.registryValueExists(root, keyPath, valueName)) return null;
            return Advapi32Util.registryGetValue(root, keyPath, valueName);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Write ───────────────────────────────────────────────────────────

    public boolean setLongValue(WinReg.HKEY root, String keyPath, String valueName, long value) {
        try {
            ensureKeyExists(root, keyPath);
            Advapi32Util.registrySetLongValue(root, keyPath, valueName, value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean setBinaryValue(WinReg.HKEY root, String keyPath, String valueName, byte[] data) {
        try {
            ensureKeyExists(root, keyPath);
            Advapi32Util.registrySetBinaryValue(root, keyPath, valueName, data);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Writes a DWORD (REG_DWORD) value.  Creates the key if it doesn't exist.
     *
     * @return {@code true} on success.
     */
    public boolean setIntValue(WinReg.HKEY root, String keyPath, String valueName, int value) {
        try {
            ensureKeyExists(root, keyPath);
            Advapi32Util.registrySetIntValue(root, keyPath, valueName, value);
            return true;
        } catch (Exception e) {
            System.err.println("[RegistryService] Error writing DWORD " +
                    keyPath + "\\" + valueName + " = " + value + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Writes a string (REG_SZ) value.  Creates the key if it doesn't exist.
     *
     * @return {@code true} on success.
     */
    public boolean setStringValue(WinReg.HKEY root, String keyPath, String valueName, String value) {
        try {
            ensureKeyExists(root, keyPath);
            Advapi32Util.registrySetStringValue(root, keyPath, valueName, value);
            return true;
        } catch (Exception e) {
            System.err.println("[RegistryService] Error writing string " +
                    keyPath + "\\" + valueName + " = " + value + ": " + e.getMessage());
            return false;
        }
    }

    // ── Delete ──────────────────────────────────────────────────────────

    /**
     * Deletes a single value from a registry key.
     *
     * @return {@code true} on success or if the value didn't exist.
     */
    public boolean deleteValue(WinReg.HKEY root, String keyPath, String valueName) {
        try {
            if (!valueExists(root, keyPath, valueName)) {
                return true; // nothing to delete
            }
            Advapi32Util.registryDeleteValue(root, keyPath, valueName);
            return true;
        } catch (Exception e) {
            System.err.println("[RegistryService] Error deleting value " +
                    keyPath + "\\" + valueName + ": " + e.getMessage());
            return false;
        }
    }

    // ── Existence checks ────────────────────────────────────────────────

    /**
     * Checks whether a registry key exists.
     */
    public boolean keyExists(WinReg.HKEY root, String keyPath) {
        try {
            return Advapi32Util.registryKeyExists(root, keyPath);
        } catch (Exception e) {
            System.err.println("[RegistryService] Error checking key " +
                    keyPath + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks whether a specific value exists within a registry key.
     */
    public boolean valueExists(WinReg.HKEY root, String keyPath, String valueName) {
        try {
            return Advapi32Util.registryValueExists(root, keyPath, valueName);
        } catch (Exception e) {
            System.err.println("[RegistryService] Error checking value " +
                    keyPath + "\\" + valueName + ": " + e.getMessage());
            return false;
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /**
     * Creates the registry key (and any missing parent keys) if it doesn't
     * already exist.
     */
    private void ensureKeyExists(WinReg.HKEY root, String keyPath) {
        if (!keyExists(root, keyPath)) {
            try {
                Advapi32Util.registryCreateKey(root, keyPath);
            } catch (Exception e) {
                System.err.println("[RegistryService] Error creating key " +
                        keyPath + ": " + e.getMessage());
            }
        }
    }
}
