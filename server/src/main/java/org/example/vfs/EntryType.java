package org.example.vfs;

public enum EntryType {
    FILE, DIRECTORY;
    @Override
    public String toString() {
        return name().toLowerCase();
    }
}