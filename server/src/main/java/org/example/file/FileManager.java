package org.example.file;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.text.SimpleDateFormat;

public class FileManager {
    private static final String SERVER_ROOT = "server_root";
    private static final String METADATA_FILE = "server_metadata.json";
    private final Path serverRootPath;
    private final Map<String, FileMetadata> metadata;

    public FileManager() throws IOException {
        this.serverRootPath = Paths.get(SERVER_ROOT).toAbsolutePath();
        this.metadata = new ConcurrentHashMap<>();

        if (!Files.exists(serverRootPath)) {
            Files.createDirectories(serverRootPath);
            System.out.println("Created server root directory: " + serverRootPath);
        }

        Path metadataPath = serverRootPath.resolve(METADATA_FILE);
        if (!Files.exists(metadataPath)) {
            Files.writeString(metadataPath, "{}");
        }

        loadMetadata();
    }

    private void loadMetadata() {
        try {
            Path metadataPath = serverRootPath.resolve(METADATA_FILE);
            if (Files.exists(metadataPath)) {
                String content = Files.readString(metadataPath);
                if (!content.trim().isEmpty() && !content.equals("{}")) {
                    parseMetadata(content);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load metadata: " + e.getMessage());
        }
    }

    private void parseMetadata(String jsonContent) {
        try {
            jsonContent = jsonContent.trim().substring(1, jsonContent.length() - 1);
            if (jsonContent.isEmpty()) return;

            String[] entries = jsonContent.split("(?<=}),(?=\\s*\")");
            for (String entry : entries) {
                String[] parts = entry.split(":", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim().replace("\"", "");
                    String value = parts[1].trim();
                    FileMetadata meta = parseMetadataEntry(value);
                    if (meta != null) {
                        metadata.put(key, meta);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing metadata: " + e.getMessage());
        }
    }

    private FileMetadata parseMetadataEntry(String json) {
        try {
            FileMetadata meta = new FileMetadata();
            json = json.substring(1, json.length() - 1);
            String[] fields = json.split(",");

            for (String field : fields) {
                String[] kv = field.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim().replace("\"", "");
                    String value = kv[1].trim().replace("\"", "");

                    switch (key) {
                        case "createdBy" -> meta.setCreatedBy(value);
                        case "clientId" -> meta.setClientId(Integer.parseInt(value));
                        case "createdAt" -> {
                            try {
                                SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");
                                meta.setCreatedAt(sdf.parse(value));
                            } catch (Exception e) {
                                meta.setCreatedAt(new Date());
                            }
                        }
                        case "size" -> meta.setSize(Long.parseLong(value));
                        case "type" -> meta.setType(value);
                    }
                }
            }
            return meta;
        } catch (Exception e) {
            System.err.println("Error parsing metadata entry: " + e.getMessage());
            return null;
        }
    }

    private void saveMetadata() {
        try {
            Path metadataPath = serverRootPath.resolve(METADATA_FILE);
            StringBuilder json = new StringBuilder();
            json.append("{\n");

            Iterator<Map.Entry<String, FileMetadata>> iterator = metadata.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, FileMetadata> entry = iterator.next();
                json.append("  \"").append(entry.getKey()).append("\": ").append(entry.getValue().toJson());
                if (iterator.hasNext()) {
                    json.append(",");
                }
                json.append("\n");
            }

            json.append("}");
            Files.writeString(metadataPath, json.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("Failed to save metadata: " + e.getMessage());
        }
    }

    public boolean delete(String path) throws IOException {
        Path targetPath = resolvePath(path);
        if (!Files.exists(targetPath)) {
            return false;
        }

        try {
            if (Files.isDirectory(targetPath)) {
                try (Stream<Path> pathStream = Files.walk(targetPath)) {
                    pathStream
                            .sorted(Comparator.reverseOrder())
                            .forEach(p -> {
                                try {
                                    Files.delete(p);
                                } catch (IOException e) {
                                    throw new RuntimeException("Failed to delete: " + p, e);
                                }
                            });
                }
            } else {
                Files.delete(targetPath);
            }

            String relativePath = serverRootPath.relativize(targetPath).toString();
            metadata.remove(relativePath);
            saveMetadata();

            return true;
        } catch (Exception e) {
            throw new IOException("Failed to delete: " + path, e);
        }
    }

    public List<String> listFiles(String path) throws IOException {
        Path targetPath = resolvePath(path);
        if (!Files.exists(targetPath) || !Files.isDirectory(targetPath)) {
            throw new IOException("Directory not found: " + path);
        }

        List<String> files = new ArrayList<>();
        try (Stream<Path> pathStream = Files.list(targetPath)) {
            pathStream.forEach(p -> files.add(p.getFileName().toString()));
        }
        return files;
    }

    public byte[] getFile(String path) throws IOException {
        Path targetPath = resolvePath(path);
        if (!Files.exists(targetPath) || Files.isDirectory(targetPath)) {
            throw new IOException("File not found: " + path);
        }
        return Files.readAllBytes(targetPath);
    }

    public boolean createDirectory(String path, int clientId, String username) throws IOException {
        Path targetPath = resolvePath(path);
        if (Files.exists(targetPath)) {
            return false;
        }
        Files.createDirectories(targetPath);

        FileMetadata meta = new FileMetadata();
        meta.setCreatedBy(username);
        meta.setClientId(clientId);
        meta.setCreatedAt(new Date());
        meta.setSize(0);
        meta.setType("directory");

        String relativePath = serverRootPath.relativize(targetPath).toString();
        metadata.put(relativePath, meta);
        saveMetadata();

        return true;
    }

    public boolean putFile(String path, byte[] data, int clientId, String username) throws IOException {
        Path targetPath = resolvePath(path);
        Path parent = targetPath.getParent();
        if (!Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        Files.write(targetPath, data);

        FileMetadata meta = new FileMetadata();
        meta.setCreatedBy(username);
        meta.setClientId(clientId);
        meta.setCreatedAt(new Date());
        meta.setSize(data.length);

        String fileName = targetPath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String type = dotIndex > 0 ? fileName.substring(dotIndex + 1) : "unknown";
        meta.setType(type);

        String relativePath = serverRootPath.relativize(targetPath).toString();
        metadata.put(relativePath, meta);
        saveMetadata();

        return true;
    }

    private Path resolvePath(String path) throws IOException {
        Path resolved;
        if (path.startsWith("/")) {
            resolved = serverRootPath.resolve(path.substring(1)).normalize();
        } else {
            resolved = serverRootPath.resolve(path).normalize();
        }

        if (!resolved.startsWith(serverRootPath)) {
            throw new IOException("Access denied: path outside server root");
        }

        return resolved;
    }

    public Path getServerRoot() {
        return serverRootPath;
    }
}