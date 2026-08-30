package com.jobhub.datamanagement.application;

import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.common.version.VersionCheck;
import com.jobhub.datamanagement.domain.TrashItem;
import com.jobhub.datamanagement.infrastructure.TrashMapper;
import com.jobhub.evidence.infrastructure.EvidenceMapper;
import com.jobhub.evidence.infrastructure.EvidenceAttachmentMapper;
import com.jobhub.evidence.domain.EvidenceAttachment;
import com.jobhub.evidence.infrastructure.ProjectMapper;
import com.jobhub.review.infrastructure.QuestionMapper;
import com.jobhub.application.infrastructure.ApplicationMapper;
import com.jobhub.interview.infrastructure.InterviewMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class TrashService {
	public static final String TYPE_PROJECT_CASE = "PROJECT_CASE";
	public static final String TYPE_EVIDENCE = "EVIDENCE";
	public static final String TYPE_INTERVIEW_QUESTION = "INTERVIEW_QUESTION";
	public static final String TYPE_EVIDENCE_ATTACHMENT = "EVIDENCE_ATTACHMENT";
	public static final String TYPE_APPLICATION = "APPLICATION";
	public static final String TYPE_INTERVIEW = "INTERVIEW";

	private static final Duration RETENTION = Duration.ofDays(30);
	private static final ObjectMapper JSON = new ObjectMapper();

	private final TrashMapper trashMapper;
	private final ProjectMapper projectMapper;
	private final EvidenceMapper evidenceMapper;
	private final EvidenceAttachmentMapper evidenceAttachmentMapper;
	private final QuestionMapper questionMapper;
	private final ApplicationMapper applicationMapper;
	private final InterviewMapper interviewMapper;
	private final IdGenerator ids;
	private final UtcTime time;

	public TrashService(TrashMapper trashMapper, ProjectMapper projectMapper, EvidenceMapper evidenceMapper,
			EvidenceAttachmentMapper evidenceAttachmentMapper, QuestionMapper questionMapper, ApplicationMapper applicationMapper,
			InterviewMapper interviewMapper, IdGenerator ids, UtcTime time) {
		this.trashMapper = trashMapper;
		this.projectMapper = projectMapper;
		this.evidenceMapper = evidenceMapper;
		this.evidenceAttachmentMapper = evidenceAttachmentMapper;
		this.questionMapper = questionMapper;
		this.applicationMapper = applicationMapper;
		this.interviewMapper = interviewMapper;
		this.ids = ids;
		this.time = time;
	}

	/**
	 * 记录一次软删除。由各资源服务在软删除成功的同一事务内调用。
	 * impactSummary 面向用户展示直接和间接影响，格式如 "2 个项目案例引用"。
	 */
	public void recordDeletion(String resourceType, String resourceId, String displayName,
			List<String> impactSummary, String now) {
		String expiresAt = DateTimeFormatter.ISO_INSTANT.format(Instant.parse(now).plus(RETENTION));
		trashMapper.insert(ids.newId(), resourceType, resourceId, displayName,
				writeJson(impactSummary), now, expiresAt);
	}

	public List<TrashItem> list() {
		return trashMapper.selectActive();
	}

	@Transactional
	public TrashItem restore(String trashId) {
		TrashItem item = requireActive(trashId);
		String now = time.now();
		String resourceId = item.getResourceId();
		boolean restored = switch (item.getResourceType()) {
			case TYPE_EVIDENCE -> evidenceMapper.restoreById(resourceId, now) > 0;
			case TYPE_PROJECT_CASE -> projectMapper.restoreById(resourceId, now) > 0;
			case TYPE_INTERVIEW_QUESTION -> questionMapper.restoreById(resourceId, now) > 0;
			case TYPE_EVIDENCE_ATTACHMENT -> evidenceAttachmentMapper.restoreById(resourceId, now) > 0;
			case TYPE_APPLICATION -> applicationMapper.restoreById(resourceId, now) > 0;
			case TYPE_INTERVIEW -> interviewMapper.restoreById(resourceId, now) > 0;
			default -> throw new BusinessRuleException("Unsupported trash resource type: " + item.getResourceType());
		};
		if (!restored) {
			throw new BusinessRuleException("原始记录不存在，无法恢复");
		}
		VersionCheck.requireAffected(trashMapper.markRestored(trashId, now), 1);
		return item;
	}

	/**
	 * 永久删除。被项目案例或技能引用的证据拒绝清除（不能静默永久删除），
	 * 项目清除自身证据关联，问题清除知识点关联与任务来源。
	 */
	@Transactional
	public void purge(String trashId) {
		TrashItem item = requireActive(trashId);
		String resourceId = item.getResourceId();
		switch (item.getResourceType()) {
			case TYPE_EVIDENCE -> {
				if (evidenceMapper.countProjectRefs(resourceId) > 0 || evidenceMapper.countSkillRefs(resourceId) > 0) {
					throw new BusinessRuleException("证据仍被项目案例或技能引用，不能永久删除；请先在引用方移除关联");
				}
				for (EvidenceAttachment attachment : evidenceAttachmentMapper.selectByEvidenceIncludeTrashed(resourceId)) {
					trashMapper.markPurgedForResource(TYPE_EVIDENCE_ATTACHMENT, attachment.getId(), time.now());
				}
				evidenceAttachmentMapper.hardDeleteByEvidence(resourceId);
				evidenceMapper.deleteProjectRefs(resourceId);
				evidenceMapper.deleteSkillRefs(resourceId);
				evidenceMapper.hardDelete(resourceId);
			}
			case TYPE_EVIDENCE_ATTACHMENT -> evidenceAttachmentMapper.hardDelete(resourceId);
			case TYPE_PROJECT_CASE -> {
				projectMapper.deleteEvidenceRefs(resourceId);
				projectMapper.hardDelete(resourceId);
			}
			case TYPE_INTERVIEW_QUESTION -> {
				questionMapper.deleteKnowledgeForQuestion(resourceId);
				questionMapper.deleteTaskSourceForQuestion(resourceId);
				questionMapper.hardDelete(resourceId);
			}
			default -> throw new BusinessRuleException("Unsupported trash resource type: " + item.getResourceType());
		}
		VersionCheck.requireAffected(trashMapper.markPurged(trashId, time.now()), 1);
	}

	private TrashItem requireActive(String trashId) {
		TrashItem item = trashMapper.selectById(trashId);
		VersionCheck.requireFound(item, "TrashItem", trashId);
		if (item.getRestoredAt() != null || item.getPurgedAt() != null) {
			throw new com.jobhub.common.error.ResourceNotFoundException("TrashItem", trashId);
		}
		return item;
	}

	private String writeJson(List<String> values) {
		try {
			return JSON.writeValueAsString(values == null ? List.of() : values);
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}
}
