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
import org.example.vfs.FileEntry;
import org.example.vfs.EntryType;

/**
 * Virtual file system for the distributed server.
 * Uses modern Java features and best practices.
 */
public class VirtualFileSystem {
    private final Path serverRoot;
    private final Map<String, FileEntry> fileTable;
    private final Map<Integer, List<String>> clientFiles;

    /**
     * Constructs a VirtualFileSystem with the specified root path.
     *
     * @param rootPath the root path for the virtual file system
     * @throws IOException if an I/O error occurs
     */
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

    /**
     * Loads existing files from the server metadata and file system.
     *
     * @throws IOException if an I/O error occurs
     */
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

                        FileEntry entry = new FileEntry(path, size, owner, ownerId, new Date(), EntryType.valueOf(type.toUpperCase()));
                        fileTable.put(path, entry);
                        if (ownerId > 0) {
                            clientFiles.computeIfAbsent(ownerId, unused -> new ArrayList<>()).add(path);
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
        // Replace statement lambda with expression lambda for fileTable.forEach
        fileTable.forEach((path, entry) -> sb.append(String.format("  \"%s\": {\"createdBy\":\"AUTH:%s\",\"clientId\":%d,\"createdAt\":\"%s\",\"size\":%d,\"type\":\"%s\"},\n",
                path, entry.owner(), entry.ownerId(), entry.createdAt(), entry.size(), entry.type())));
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

    /**
     * Creates a new file or directory in the virtual file system.
     *
     * @param path    the path of the file or directory to create
     * @param data    the data for the file, or null for a directory
     * @param owner   the owner of the file
     * @param clientId the client ID associated with the file
     * @param type    the type of the entry ("file" or "directory")
     * @return the created FileEntry
     * @throws IOException if an I/O error occurs
     */
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
                type.equals("directory") ? EntryType.DIRECTORY : EntryType.FILE
        );

        fileTable.put(path, entry);


        // Replace statement lambda with expression lambda for clientFiles.computeIfAbsent
        clientFiles.computeIfAbsent(clientId, unused -> new ArrayList<>()).add(path);

        saveMetadata();

        return entry;
    }

    /**
     * Creates a new file in the virtual file system.
     *
     * @param path    the path of the file to create
     * @param data    the data for the file
     * @param owner   the owner of the file
     * @param clientId the client ID associated with the file
     * @return the created FileEntry
     * @throws IOException if an I/O error occurs
     */
    public FileEntry createFile(String path, byte[] data, String owner, int clientId) throws IOException {
        return createFile(path, data, owner, clientId, "file");
    }

    /**
     * Reads the contents of a file.
     *
     * @param path the path of the file to read
     * @return the file data as a byte array
     * @throws IOException if an I/O error occurs
     */
    public byte[] readFile(String path) throws IOException {
        FileEntry entry = fileTable.get(path);
        if (entry == null) {
            throw new FileNotFoundException("File not found: " + path);
        }

        if (entry.type() == EntryType.DIRECTORY) {
            throw new IOException("Cannot read a directory: " + path);
        }

        Path fullPath = serverRoot.resolve(path);
        return Files.readAllBytes(fullPath);
    }

    /**
     * Deletes a file or directory from the virtual file system.
     *
     * @param path    the path of the file or directory to delete
     * @param clientId the client ID attempting the deletion
     * @return true if the file or directory was deleted, false if not found
     * @throws IOException if an I/O error occurs
     */
    public boolean deleteFile(String path, int clientId) throws IOException {
        FileEntry entry = fileTable.get(path);
        if (entry == null) {
            return false;
        }


        if (entry.ownerId() != clientId && !entry.owner().equals("admin")) {
            throw new SecurityException("Permission denied");
        }

        Path fullPath = serverRoot.resolve(path);
        if (entry.type() == EntryType.DIRECTORY) {

            // Replace statement lambda with expression lambda for fileTable.keySet().stream().anyMatch
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

    /**
     * Lists files in a directory.
     *
     * @param directory the directory to list files from
     * @return a list of FileEntry objects representing the files in the directory
     */
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

    /**
     * Retrieves files associated with a specific client.
     *
     * @param clientId the client ID
     * @return a list of FileEntry objects associated with the client
     */
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
}
