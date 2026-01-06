package org.example.vfs;

import java.util.Date;
import java.nio.file.Path;
import java.io.IOException;

/**
 * Immutable file entry for the virtual file system.
 */
public record FileEntry(String path, long size, String owner, int ownerId, Date createdAt, EntryType type) {
    public static FileEntry fromPath(Path path) throws IOException {
        return new FileEntry(
                path.toString(),
                java.nio.file.Files.size(path),
                "system",
                0,
                java.nio.file.Files.getLastModifiedTime(path).toInstant().toEpochMilli() > 0 ?
                        new Date(java.nio.file.Files.getLastModifiedTime(path).toMillis()) : new Date(),
                java.nio.file.Files.isDirectory(path) ? EntryType.DIRECTORY : EntryType.FILE
        );
    }
    @Override
    public String toString() {
        return String.format("%s [%s] %dB - %s (%s)",
                path, type, size, owner, createdAt);
    }
}

