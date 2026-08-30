package com.jobhub.evidence.api;

import com.jobhub.evidence.application.EvidenceAttachmentCreateCommand;
import com.jobhub.evidence.application.EvidenceAttachmentService;
import com.jobhub.evidence.domain.EvidenceAttachment;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api")
public class EvidenceAttachmentController {
    private final EvidenceAttachmentService service;

    public EvidenceAttachmentController(EvidenceAttachmentService service) {
        this.service = service;
    }

    @GetMapping("/evidence-attachments")
    public List<EvidenceAttachmentResponse> list(@RequestParam(required = false) String evidenceId) {
        return service.list(evidenceId).stream().map(EvidenceAttachmentResponse::from).toList();
    }

    @PostMapping("/evidence/{evidenceId}/attachments")
    public ResponseEntity<EvidenceAttachmentResponse> create(@PathVariable String evidenceId,
            @Valid @RequestBody EvidenceAttachmentCreateRequest request) {
        EvidenceAttachment attachment = service.create(evidenceId, toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(EvidenceAttachmentResponse.from(attachment));
    }

    @PutMapping("/evidence-attachments/{attachmentId}")
    public ResponseEntity<EvidenceAttachmentResponse> update(@PathVariable String attachmentId,
            @RequestHeader(value = "If-Match-Version", required = false) Long version,
            @Valid @RequestBody EvidenceAttachmentCreateRequest request) {
        if (version == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(EvidenceAttachmentResponse.from(service.update(attachmentId, version, toCommand(request))));
    }

    @DeleteMapping("/evidence-attachments/{attachmentId}")
    public ResponseEntity<Void> delete(@PathVariable String attachmentId,
            @RequestHeader(value = "If-Match-Version", required = false) Long version) {
        if (version == null) return ResponseEntity.badRequest().build();
        service.delete(attachmentId, version);
        return ResponseEntity.noContent().build();
    }

    private EvidenceAttachmentCreateCommand toCommand(EvidenceAttachmentCreateRequest request) {
        return new EvidenceAttachmentCreateCommand(request.displayName(), request.sourceType(), request.location(),
            request.mediaType(), request.sizeBytes(), request.description());
    }
}
