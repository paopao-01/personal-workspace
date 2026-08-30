package com.jobhub.evidence.api;

import com.jobhub.evidence.domain.EvidenceAttachment;
import com.jobhub.evidence.domain.AttachmentSourceType;

public record EvidenceAttachmentResponse(
    String id,
    String evidenceId,
    String evidenceTitle,
    String displayName,
    AttachmentSourceType sourceType,
    String location,
    String mediaType,
    Long sizeBytes,
    String description,
    String createdAt,
    String updatedAt,
    long version
) {
    public static EvidenceAttachmentResponse from(EvidenceAttachment attachment) {
        return new EvidenceAttachmentResponse(attachment.getId(), attachment.getEvidenceId(),
            attachment.getEvidenceTitle(), attachment.getDisplayName(), attachment.getSourceType(),
            attachment.getLocation(), attachment.getMediaType(), attachment.getSizeBytes(),
            attachment.getDescription(), attachment.getCreatedAt(), attachment.getUpdatedAt(),
            attachment.getVersion());
    }
}
