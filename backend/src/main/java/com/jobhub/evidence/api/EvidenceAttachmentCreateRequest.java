package com.jobhub.evidence.api;

import com.jobhub.evidence.domain.AttachmentSourceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record EvidenceAttachmentCreateRequest(
    @NotBlank @Size(max = 200) String displayName,
    @NotNull AttachmentSourceType sourceType,
    @NotBlank @Size(max = 2000) String location,
    @Size(max = 100) String mediaType,
    @PositiveOrZero @Max(2199023255552L) Long sizeBytes,
    @Size(max = 1000) String description
) { }
