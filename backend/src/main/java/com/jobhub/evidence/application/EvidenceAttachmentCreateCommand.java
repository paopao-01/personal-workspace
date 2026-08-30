package com.jobhub.evidence.application;

import com.jobhub.evidence.domain.AttachmentSourceType;

public record EvidenceAttachmentCreateCommand(
    String displayName,
    AttachmentSourceType sourceType,
    String location,
    String mediaType,
    Long sizeBytes,
    String description
) { }
