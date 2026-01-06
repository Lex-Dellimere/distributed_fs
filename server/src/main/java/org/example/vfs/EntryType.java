package org.example.vfs;

/**
 * Enum for file entry types in the virtual file system.
 */
public enum EntryType {
    FILE, DIRECTORY;
    @Override
    public String toString() {
        return name().toLowerCase();
    }
}