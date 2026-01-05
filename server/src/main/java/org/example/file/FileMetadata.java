package org.example.file;

import java.util.Date;

public class FileMetadata {
    private String createdBy;
    private int clientId;
    private Date createdAt;
    private long size;
    private String type;

    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setClientId(int clientId) { this.clientId = clientId; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public void setSize(long size) { this.size = size; }
    public void setType(String type) { this.type = type; }

    public String toJson() {
        return String.format(
                "{\"createdBy\":\"%s\",\"clientId\":%d,\"createdAt\":\"%s\",\"size\":%d,\"type\":\"%s\"}",
                createdBy, clientId, createdAt, size, type
        );
    }
}