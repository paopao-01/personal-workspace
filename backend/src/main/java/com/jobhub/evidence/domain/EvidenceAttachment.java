package com.jobhub.evidence.domain;

/**
 * 证据附件引用元数据。location 只保存用户填写的路径或链接，系统不读取、扫描、上传或下载其内容。
 */
public class EvidenceAttachment {
    private String id;
    private String evidenceId;
    private String evidenceTitle;
    private String displayName;
    private AttachmentSourceType sourceType;
    private String location;
    private String mediaType;
    private Long sizeBytes;
    private String description;
    private String createdAt;
    private String updatedAt;
    private String deletedAt;
    private long version;

    public static EvidenceAttachment create(String id, String evidenceId, String displayName,
            AttachmentSourceType sourceType, String location, String mediaType, Long sizeBytes,
            String description, String now) {
        EvidenceAttachment attachment = new EvidenceAttachment();
        attachment.id = id;
        attachment.evidenceId = evidenceId;
        attachment.displayName = displayName;
        attachment.sourceType = sourceType;
        attachment.location = location;
        attachment.mediaType = mediaType;
        attachment.sizeBytes = sizeBytes;
        attachment.description = description;
        attachment.createdAt = now;
        attachment.updatedAt = now;
        return attachment;
    }

    public void updateMeta(String displayName, AttachmentSourceType sourceType, String location,
            String mediaType, Long sizeBytes, String description, String now) {
        this.displayName = displayName;
        this.sourceType = sourceType;
        this.location = location;
        this.mediaType = mediaType;
        this.sizeBytes = sizeBytes;
        this.description = description;
        this.updatedAt = now;
    }

    public String getId() { return id; }
    public String getEvidenceId() { return evidenceId; }
    public String getEvidenceTitle() { return evidenceTitle; }
    public String getDisplayName() { return displayName; }
    public AttachmentSourceType getSourceType() { return sourceType; }
    public String getLocation() { return location; }
    public String getMediaType() { return mediaType; }
    public Long getSizeBytes() { return sizeBytes; }
    public String getDescription() { return description; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public String getDeletedAt() { return deletedAt; }
    public long getVersion() { return version; }
}
