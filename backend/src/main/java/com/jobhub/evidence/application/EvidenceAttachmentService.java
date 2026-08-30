package com.jobhub.evidence.application;

import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.common.version.VersionCheck;
import com.jobhub.datamanagement.application.TrashService;
import com.jobhub.evidence.domain.EvidenceAttachment;
import com.jobhub.evidence.infrastructure.EvidenceAttachmentMapper;
import com.jobhub.evidence.infrastructure.EvidenceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class EvidenceAttachmentService {
    private final EvidenceAttachmentMapper attachmentMapper;
    private final EvidenceMapper evidenceMapper;
    private final TrashService trashService;
    private final IdGenerator ids;
    private final UtcTime time;

    public EvidenceAttachmentService(EvidenceAttachmentMapper attachmentMapper, EvidenceMapper evidenceMapper,
            TrashService trashService, IdGenerator ids, UtcTime time) {
        this.attachmentMapper = attachmentMapper;
        this.evidenceMapper = evidenceMapper;
        this.trashService = trashService;
        this.ids = ids;
        this.time = time;
    }

    public List<EvidenceAttachment> list(String evidenceId) {
        return attachmentMapper.selectActive(blankToNull(evidenceId));
    }

    @Transactional
    public EvidenceAttachment create(String evidenceId, EvidenceAttachmentCreateCommand command) {
        VersionCheck.requireFound(evidenceMapper.selectById(evidenceId), "Evidence", evidenceId);
        String now = time.now();
        EvidenceAttachment attachment = EvidenceAttachment.create(ids.newId(), evidenceId,
            required(command.displayName(), "附件名称不能为空"), command.sourceType(),
            required(command.location(), "附件位置不能为空"), blankToNull(command.mediaType()),
            command.sizeBytes(), blankToNull(command.description()), now);
        attachmentMapper.insert(attachment);
        return get(attachment.getId());
    }

    @Transactional
    public EvidenceAttachment update(String id, long expectedVersion, EvidenceAttachmentCreateCommand command) {
        EvidenceAttachment attachment = requireAttachment(id);
        attachment.updateMeta(required(command.displayName(), "附件名称不能为空"), command.sourceType(),
            required(command.location(), "附件位置不能为空"), blankToNull(command.mediaType()), command.sizeBytes(),
            blankToNull(command.description()), time.now());
        VersionCheck.requireAffected(attachmentMapper.updateMeta(attachment, expectedVersion), 1);
        VersionCheck.requireAffected(attachmentMapper.bumpVersion(id, expectedVersion), 1);
        return get(id);
    }

    @Transactional
    public void delete(String id, long expectedVersion) {
        EvidenceAttachment attachment = requireAttachment(id);
        String now = time.now();
        VersionCheck.requireAffected(attachmentMapper.softDelete(id, expectedVersion, now), 1);
        trashService.recordDeletion(TrashService.TYPE_EVIDENCE_ATTACHMENT, id, attachment.getDisplayName(),
            List.of("所属证据：" + attachment.getEvidenceTitle()), now);
    }

    private EvidenceAttachment get(String id) {
        EvidenceAttachment attachment = requireAttachment(id);
        return attachment;
    }

    private EvidenceAttachment requireAttachment(String id) {
        EvidenceAttachment attachment = attachmentMapper.selectById(id);
        VersionCheck.requireFound(attachment, "EvidenceAttachment", id);
        return attachment;
    }

    private String required(String value, String message) {
        String normalized = blankToNull(value);
        if (normalized == null) throw new com.jobhub.common.error.BusinessRuleException(message);
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
