package com.nextgen.optimizer.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sun.jna.platform.win32.WinReg;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Saves and restores system state (primarily registry values) before and after
 * optimisations are applied.
 * <p>
 * Backups are stored as individual JSON files under
 * {@code <user-home>/.nextgen/backups/}.
 */
public class BackupService {

    // ── Inner DTOs ──────────────────────────────────────────────────────

    /**
     * A single registry value that was captured before an optimisation.
     */
    public static class BackupEntry {
        private String key;
        private String registryPath;
        private String valueName;
        private Object originalValue;
        private String type;          // "DWORD" or "STRING"
        private String description;
        private long timestamp;

        public BackupEntry() { }

        public BackupEntry(String key, String registryPath, String valueName,
                           Object originalValue, String type, String description) {
            this.key = key;
            this.registryPath = registryPath;
            this.valueName = valueName;
            this.originalValue = originalValue;
            this.type = type;
            this.description = description;
            this.timestamp = System.currentTimeMillis();
        }

        // — Getters / Setters —
        public String getKey()                  { return key; }
        public void setKey(String key)          { this.key = key; }
        public String getRegistryPath()         { return registryPath; }
        public void setRegistryPath(String p)   { this.registryPath = p; }
        public String getValueName()            { return valueName; }
        public void setValueName(String v)      { this.valueName = v; }
        public Object getOriginalValue()        { return originalValue; }
        public void setOriginalValue(Object v)  { this.originalValue = v; }
        public String getType()                 { return type; }
        public void setType(String type)        { this.type = type; }
        public String getDescription()          { return description; }
        public void setDescription(String d)    { this.description = d; }
        public long getTimestamp()              { return timestamp; }
        public void setTimestamp(long ts)       { this.timestamp = ts; }
    }

    /**
     * A complete backup snapshot containing one or more {@link BackupEntry entries}
     * plus optional free-form system state.
     */
    public static class BackupSnapshot {
        private String id;
        private String label;
        private long timestamp;
        private List<BackupEntry> entries = new ArrayList<>();
        private Map<String, String> systemState = new LinkedHashMap<>();

        public BackupSnapshot() { }

        public BackupSnapshot(String label) {
            this.id = UUID.randomUUID().toString().substring(0, 8);
            this.label = label;
            this.timestamp = System.currentTimeMillis();
        }

        // — Getters / Setters —
        public String getId()                           { return id; }
        public void setId(String id)                    { this.id = id; }
        public String getLabel()                        { return label; }
        public void setLabel(String label)              { this.label = label; }
        public long getTimestamp()                      { return timestamp; }
        public void setTimestamp(long ts)               { this.timestamp = ts; }
        public List<BackupEntry> getEntries()           { return entries; }
        public void setEntries(List<BackupEntry> e)     { this.entries = e; }
        public Map<String, String> getSystemState()     { return systemState; }
        public void setSystemState(Map<String, String> s) { this.systemState = s; }
    }

    // ── Fields ──────────────────────────────────────────────────────────

    private final RegistryService registryService;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ── Constructors ────────────────────────────────────────────────────

    /**
     * Creates a BackupService with an injected {@link RegistryService}.
     */
    public BackupService(RegistryService registryService) {
        this.registryService = registryService;
    }

    /**
     * No-arg constructor — creates its own {@link RegistryService}.
     * Used when the caller (e.g. {@code App.java}) doesn't pass one explicitly.
     */
    public BackupService() {
        this(new RegistryService());
    }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Creates a new, empty backup snapshot with the given label.
     */
    public BackupSnapshot createBackup(String label) {
        return new BackupSnapshot(label);
    }

    /**
     * Reads the current value of a registry entry and adds it to the snapshot.
     *
     * @param snapshot     target snapshot to add the entry to.
     * @param registryPath the subkey path, e.g. {@code "SYSTEM\\CurrentControlSet\\..."}.
     * @param valueName    the value name inside the key.
     * @param root         the root hive, e.g. {@link WinReg#HKEY_LOCAL_MACHINE}.
     */
    public void addEntry(BackupSnapshot snapshot, String registryPath,
                         String valueName, WinReg.HKEY root) {
        if (snapshot == null) return;

        try {
            String key = rootToString(root) + "\\" + registryPath + "\\" + valueName;
            Object originalValue = null;
            String type = "DWORD"; // default

            if (registryService.valueExists(root, registryPath, valueName)) {
                // Try reading as DWORD first
                int intVal = registryService.getIntValue(root, registryPath, valueName);
                if (intVal != -1) {
                    originalValue = intVal;
                    type = "DWORD";
                } else {
                    // Fall back to string
                    String strVal = registryService.getStringValue(root, registryPath, valueName);
                    originalValue = strVal;
                    type = "STRING";
                }
            }
            // If value doesn't exist, we still record it with null originalValue
            // so restore knows to delete it

            BackupEntry entry = new BackupEntry(
                    key, registryPath, valueName,
                    originalValue, type,
                    "Backup of " + valueName
            );
            snapshot.getEntries().add(entry);

        } catch (Exception e) {
            System.err.println("[BackupService] Error adding entry " +
                    registryPath + "\\" + valueName + ": " + e.getMessage());
        }
    }

    /**
     * Persists the snapshot to a JSON file in the backup directory.
     *
     * @return {@code true} on success.
     */
    public boolean saveBackup(BackupSnapshot snapshot) {
        if (snapshot == null) return false;

        try {
            Path dir = getBackupDir();
            Files.createDirectories(dir);
            Path file = dir.resolve("backup_" + snapshot.getId() + ".json");
            String json = GSON.toJson(snapshot);
            Files.writeString(file, json, StandardCharsets.UTF_8);
            System.out.println("[BackupService] Saved backup '" + snapshot.getLabel() +
                    "' → " + file.getFileName());
            return true;
        } catch (IOException e) {
            System.err.println("[BackupService] Failed to save backup: " + e.getMessage());
            return false;
        }
    }

    /**
     * Restores all entries from a previously saved backup.
     *
     * @param snapshotId the 8-char UUID prefix used as the file name.
     * @return {@code true} if all entries were restored successfully.
     */
    public boolean restoreBackup(String snapshotId) {
        BackupSnapshot snapshot = loadSnapshot(snapshotId);
        if (snapshot == null) {
            System.err.println("[BackupService] Backup not found: " + snapshotId);
            return false;
        }

        boolean allOk = true;
        for (BackupEntry entry : snapshot.getEntries()) {
            WinReg.HKEY root = stringToRoot(entry.getKey());
            if (root == null) {
                root = WinReg.HKEY_LOCAL_MACHINE; // default fallback
            }

            try {
                if (entry.getOriginalValue() == null) {
                    // Value didn't exist before — delete it to restore original state
                    if (!registryService.deleteValue(root, entry.getRegistryPath(), entry.getValueName())) {
                        allOk = false;
                    }
                } else if ("DWORD".equalsIgnoreCase(entry.getType())) {
                    int val = ((Number) entry.getOriginalValue()).intValue();
                    if (!registryService.setIntValue(root, entry.getRegistryPath(), entry.getValueName(), val)) {
                        allOk = false;
                    }
                } else {
                    String val = String.valueOf(entry.getOriginalValue());
                    if (!registryService.setStringValue(root, entry.getRegistryPath(), entry.getValueName(), val)) {
                        allOk = false;
                    }
                }
            } catch (Exception e) {
                System.err.println("[BackupService] Error restoring entry " +
                        entry.getKey() + ": " + e.getMessage());
                allOk = false;
            }
        }

        System.out.println("[BackupService] Restore of '" + snapshot.getLabel() +
                "' " + (allOk ? "completed successfully." : "completed with errors."));
        return allOk;
    }

    public boolean restoreBackup(BackupSnapshot snapshot) {
        if (snapshot == null) return false;
        return restoreBackup(snapshot.getId());
    }

    public BackupSnapshot loadLatestBackup(String labelPrefix) {
        List<BackupSnapshot> backups = listBackups();
        for (BackupSnapshot s : backups) {
            if (s.getLabel() != null && s.getLabel().startsWith(labelPrefix)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Lists all saved backup snapshots, sorted newest-first.
     */
    public List<BackupSnapshot> listBackups() {
        List<BackupSnapshot> backups = new ArrayList<>();
        Path dir = getBackupDir();

        if (!Files.isDirectory(dir)) {
            return backups;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "backup_*.json")) {
            for (Path file : stream) {
                try {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    BackupSnapshot snapshot = GSON.fromJson(json, BackupSnapshot.class);
                    if (snapshot != null) {
                        backups.add(snapshot);
                    }
                } catch (Exception e) {
                    System.err.println("[BackupService] Skipping corrupt backup file: " +
                            file.getFileName() + " — " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[BackupService] Error listing backups: " + e.getMessage());
        }

        backups.sort(Comparator.comparingLong(BackupSnapshot::getTimestamp).reversed());
        return backups;
    }

    /**
     * Deletes a saved backup file.
     *
     * @return {@code true} if the file was deleted (or never existed).
     */
    public boolean deleteBackup(String snapshotId) {
        Path file = getBackupDir().resolve("backup_" + snapshotId + ".json");
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            System.err.println("[BackupService] Error deleting backup " +
                    snapshotId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Returns the directory where backups are stored.
     */
    public Path getBackupDir() {
        return Path.of(System.getProperty("user.home"), ".nextgen", "backups");
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private BackupSnapshot loadSnapshot(String snapshotId) {
        Path file = getBackupDir().resolve("backup_" + snapshotId + ".json");
        if (!Files.exists(file)) {
            return null;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return GSON.fromJson(json, BackupSnapshot.class);
        } catch (Exception e) {
            System.err.println("[BackupService] Error loading backup " +
                    snapshotId + ": " + e.getMessage());
            return null;
        }
    }

    private static String rootToString(WinReg.HKEY root) {
        if (WinReg.HKEY_LOCAL_MACHINE.equals(root))  return "HKLM";
        if (WinReg.HKEY_CURRENT_USER.equals(root))   return "HKCU";
        if (WinReg.HKEY_CLASSES_ROOT.equals(root))    return "HKCR";
        if (WinReg.HKEY_USERS.equals(root))           return "HKU";
        return "HKLM";
    }

    private static WinReg.HKEY stringToRoot(String key) {
        if (key == null) return null;
        String upper = key.toUpperCase();
        if (upper.startsWith("HKLM"))  return WinReg.HKEY_LOCAL_MACHINE;
        if (upper.startsWith("HKCU"))  return WinReg.HKEY_CURRENT_USER;
        if (upper.startsWith("HKCR"))  return WinReg.HKEY_CLASSES_ROOT;
        if (upper.startsWith("HKU"))   return WinReg.HKEY_USERS;
        return null;
    }
}
