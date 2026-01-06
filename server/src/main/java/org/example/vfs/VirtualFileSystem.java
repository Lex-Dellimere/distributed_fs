package org.example.vfs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualFileSystem {
    private final Path serverRoot;
    private final Map<String, FileEntry> fileTable;
    private final Map<Integer, List<String>> clientFiles;

    public VirtualFileSystem(String rootPath) throws IOException {
        this.serverRoot = Paths.get(rootPath).toAbsolutePath();
        this.fileTable = new ConcurrentHashMap<>();
        this.clientFiles = new ConcurrentHashMap<>();

        if (!Files.exists(serverRoot)) {
            Files.createDirectories(serverRoot);
        }


        Path metadataPath = serverRoot.resolve("server_metadata.json");
        if (!Files.exists(metadataPath)) {
            Files.writeString(metadataPath, "{}");
        }

        loadExistingFiles();
    }

    public void loadExistingFiles() throws IOException {
        Path metadataPath = serverRoot.resolve("server_metadata.json");
        fileTable.clear();
        clientFiles.clear();
        if (Files.exists(metadataPath)) {

            List<String> lines = Files.readAllLines(metadataPath);
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("\"") && line.contains(": {")) {
                    try {
                        String path = line.substring(1, line.indexOf("\":"));
                        String details = line.substring(line.indexOf("{") + 1, line.lastIndexOf("}"));

                        String owner = "system";
                        int ownerId = 0;
                        long size = 0;
                        String type = "file";

                        for (String part : details.split(",")) {
                            String[] kv = part.split(":", 2);
                            if (kv.length < 2) continue;
                            String key = kv[0].trim().replace("\"", "");
                            String value = kv[1].trim().replace("\"", "");

                            switch (key) {
                                case "createdBy" -> owner = value.replace("AUTH:", "");
                                case "clientId" -> ownerId = Integer.parseInt(value);
                                case "size" -> size = Long.parseLong(value);
                                case "type" -> type = value;
                            }
                        }

                        FileEntry entry = new FileEntry(path, size, owner, ownerId, new Date(), type);
                        fileTable.put(path, entry);
                        if (ownerId > 0) {
                            clientFiles.computeIfAbsent(ownerId, k -> new ArrayList<>()).add(path);
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing metadata line: " + line);
                    }
                }
            }
        }


        try (var paths = Files.walk(serverRoot)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            String relativePath = serverRoot.relativize(path).toString();
                            if (relativePath.equals("server_metadata.json")) return;

                            if (!fileTable.containsKey(relativePath)) {
                                FileEntry entry = FileEntry.fromPath(path);
                                fileTable.put(relativePath, entry);
                            }
                        } catch (IOException e) {
                            System.err.println("Failed to load file: " + path);
                        }
                    });
        }
    }

    private synchronized void saveMetadata() {
        Path metadataPath = serverRoot.resolve("server_metadata.json");
        StringBuilder sb = new StringBuilder("{\n");
        fileTable.forEach((path, entry) -> {
            sb.append(String.format("  \"%s\": {\"createdBy\":\"AUTH:%s\",\"clientId\":%d,\"createdAt\":\"%s\",\"size\":%d,\"type\":\"%s\"},\n",
                    path, entry.getOwner(), entry.getOwnerId(), entry.getCreatedAt(), entry.getSize(), entry.getType()));
        });
        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2);
            sb.append("\n");
        }
        sb.append("}");
        try {
            Files.writeString(metadataPath, sb.toString());
        } catch (IOException e) {
            System.err.println("Failed to save metadata: " + e.getMessage());
        }
    }

    public FileEntry createFile(String path, byte[] data, String owner, int clientId, String type) throws IOException {
        Path fullPath = serverRoot.resolve(path).normalize();

        if (!fullPath.startsWith(serverRoot)) {
            throw new SecurityException("Access denied: path traversal attempt");
        }

        if (type.equals("file")) {

            Files.createDirectories(fullPath.getParent());


            Files.write(fullPath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } else if (type.equals("directory")) {
            Files.createDirectories(fullPath);
        }


        FileEntry entry = new FileEntry(
                path,
                data != null ? data.length : 0,
                owner,
                clientId,
                new Date(),
                type
        );

        fileTable.put(path, entry);


        clientFiles.computeIfAbsent(clientId, k -> new ArrayList<>()).add(path);

        saveMetadata();

        return entry;
    }

    public FileEntry createFile(String path, byte[] data, String owner, int clientId) throws IOException {
        return createFile(path, data, owner, clientId, "file");
    }

    public byte[] readFile(String path) throws IOException {
        FileEntry entry = fileTable.get(path);
        if (entry == null) {
            throw new FileNotFoundException("File not found: " + path);
        }

        if (entry.getType().equals("directory")) {
            throw new IOException("Cannot read a directory: " + path);
        }

        Path fullPath = serverRoot.resolve(path);
        return Files.readAllBytes(fullPath);
    }

    public boolean deleteFile(String path, int clientId) throws IOException {
        FileEntry entry = fileTable.get(path);
        if (entry == null) {
            return false;
        }


        if (entry.getOwnerId() != clientId && !entry.getOwner().equals("admin")) {
            throw new SecurityException("Permission denied");
        }

        Path fullPath = serverRoot.resolve(path);
        if (entry.getType().equals("directory")) {

            boolean hasChildren = fileTable.keySet().stream()
                    .anyMatch(p -> p.startsWith(path + "/") && !p.equals(path));
            if (hasChildren) {
                throw new IOException("Directory not empty");
            }
            Files.deleteIfExists(fullPath);
        } else {
            Files.deleteIfExists(fullPath);
        }

        fileTable.remove(path);


        List<String> files = clientFiles.get(clientId);
        if (files != null) {
            files.remove(path);
        }

        saveMetadata();

        return true;
    }

    public List<FileEntry> listFiles(String directory) {
        List<FileEntry> result = new ArrayList<>();

        for (Map.Entry<String, FileEntry> entry : fileTable.entrySet()) {
            String path = entry.getKey();
            if (path.startsWith(directory)) {
                result.add(entry.getValue());
            }
        }

        return result;
    }

    public List<FileEntry> getClientFiles(int clientId) {
        List<FileEntry> result = new ArrayList<>();
        List<String> filePaths = clientFiles.get(clientId);

        if (filePaths != null) {
            for (String path : filePaths) {
                FileEntry entry = fileTable.get(path);
                if (entry != null) {
                    result.add(entry);
                }
            }
        }

        return result;
    }

    public static class FileEntry {
        private final String path;
        private final long size;
        private final String owner;
        private final int ownerId;
        private final Date createdAt;
        private final String type;

        public FileEntry(String path, long size, String owner, int ownerId, Date createdAt, String type) {
            this.path = path;
            this.size = size;
            this.owner = owner;
            this.ownerId = ownerId;
            this.createdAt = createdAt;
            this.type = type;
        }

        public static FileEntry fromPath(Path path) throws IOException {
            return new FileEntry(
                    path.toString(),
                    Files.size(path),
                    "system",
                    0,
                    Files.getLastModifiedTime(path).toInstant().toEpochMilli() > 0 ?
                            new Date(Files.getLastModifiedTime(path).toMillis()) : new Date(),
                    Files.isDirectory(path) ? "directory" : "file"
            );
        }


        public String getPath() {
            return path;
        }

        public long getSize() {
            return size;
        }

        public String getOwner() {
            return owner;
        }

        public int getOwnerId() {
            return ownerId;
        }

        public Date getCreatedAt() {
            return createdAt;
        }

        public String getType() {
            return type;
        }

        @Override
        public String toString() {
            return String.format("%s [%s] %dB - %s (%s)",
                    path, type, size, owner, createdAt);
        }
    }
}
