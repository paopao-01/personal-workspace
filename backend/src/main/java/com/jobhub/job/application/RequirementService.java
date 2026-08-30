package com.jobhub.job.application;

import com.jobhub.common.audit.AuditLogEntry;
import com.jobhub.common.audit.infrastructure.AuditLogMapper;
import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.common.version.VersionCheck;
import com.jobhub.job.domain.*;
import com.jobhub.job.infrastructure.JobMapper;
import com.jobhub.job.infrastructure.JobRequirementMapper;
import com.jobhub.job.infrastructure.RequirementMatchMapper;
import com.jobhub.skill.infrastructure.SkillProfileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class RequirementService {

	private final JobMapper jobMapper;
	private final JobRequirementMapper requirementMapper;
	private final RequirementMatchMapper matchMapper;
	private final AuditLogMapper auditLogMapper;
	private final IdGenerator idGenerator;
	private final UtcTime utcTime;
	private final RequirementExtractor extractor;
	private final SkillProfileMapper skillProfileMapper;

	public RequirementService(JobMapper jobMapper, JobRequirementMapper requirementMapper,
							   RequirementMatchMapper matchMapper, AuditLogMapper auditLogMapper,
							   IdGenerator idGenerator, UtcTime utcTime, RequirementExtractor extractor,
							   SkillProfileMapper skillProfileMapper) {
		this.jobMapper = jobMapper;
		this.requirementMapper = requirementMapper;
		this.matchMapper = matchMapper;
		this.auditLogMapper = auditLogMapper;
		this.idGenerator = idGenerator;
		this.utcTime = utcTime;
		this.extractor = extractor;
		this.skillProfileMapper = skillProfileMapper;
	}

	@Transactional
	public ExtractionResult extractRequirements(String jobId) {
		Job job = jobMapper.selectById(jobId);
		VersionCheck.requireFound(job, "Job", jobId);
		List<JobRequirement> existing = requirementMapper.selectByJobId(jobId);
		List<JobRequirement> candidates = extractor.extract(jobId, job.getJdRawText(), existing);
		if (candidates.isEmpty()) {
			return new ExtractionResult(List.of(), 0);
		}
		requirementMapper.batchInsert(candidates);
		for (JobRequirement candidate : candidates) {
			replaceSkillRefs(candidate, utcTime.now());
		}
		return new ExtractionResult(candidates, candidates.size());
	}

	/**
	 * AI 候选采纳（PRD 9.2）：创建 source_type=AI 的 PENDING 岗位要求，进入既有确认流。
	 */
	@Transactional
	public JobRequirement createAiCandidate(String jobId, String type, String rawText, String normalizedName,
			String proficiencyText) {
		Job job = jobMapper.selectById(jobId);
		VersionCheck.requireFound(job, "Job", jobId);
		List<JobRequirement> existing = requirementMapper.selectByJobId(jobId);
		RequirementType requirementType = RequirementType.valueOf(type);
		JobRequirement candidate = JobRequirement.createFromAi(idGenerator.newId(), jobId, rawText,
				normalizedName == null || normalizedName.isBlank() ? rawText : normalizedName,
				requirementType, proficiencyText, existing.size(), utcTime.now());
		requirementMapper.batchInsert(List.of(candidate));
		replaceSkillRefs(candidate, utcTime.now());
		return candidate;
	}

	public List<JobRequirement> listByJob(String jobId) {
		return requirementMapper.selectByJobId(jobId);
	}

	@Transactional
	public JobRequirement updateRequirement(String requirementId, long expectedVersion, RequirementUpdateCommand cmd) {
		JobRequirement req = requirementMapper.selectById(requirementId);
		VersionCheck.requireFound(req, "JobRequirement", requirementId);

		ConfirmationStatus target = cmd.confirmationStatus();
		String now = utcTime.now();
		String rawText = textOrCurrent(cmd.rawText(), req.getRawText(), "Requirement raw text is required");
		String normalizedName = textOrCurrent(cmd.normalizedName(), req.getNormalizedName(),
				"Requirement normalized name is required");
		RequirementType type = cmd.type() != null ? cmd.type() : req.getType();
		req.updateDetails(rawText, normalizedName, type,
				cmd.proficiencyText() != null ? cmd.proficiencyText() : req.getProficiencyText(), now);
		switch (target) {
			case CONFIRMED -> req.confirm(now);
			case IGNORED -> req.ignore(now);
			case PENDING -> req.restoreToPending(now);
		}

		int affected = requirementMapper.updateByIdAndVersion(req, expectedVersion);
		VersionCheck.requireAffected(affected, req.getVersion());
		int bumped = requirementMapper.bumpVersionByIdAndVersion(requirementId, expectedVersion);
		VersionCheck.requireAffected(bumped, req.getVersion());
		replaceSkillRefs(req, now);
		auditLogMapper.insert(AuditLogEntry.requirementChanged(idGenerator.newId(), requirementId,
				"REQUIREMENT_UPDATED", "User edited or confirmed requirement.", now));

		// AT-04：人工修正匹配状态。当用户提供 manualMatchStatus 时，upsert requirement_match。
		if (cmd.manualMatchStatus() != null) {
			RequirementMatch existing = matchMapper.selectByRequirementId(requirementId);
			String now2 = utcTime.now();
			if (existing == null) {
				RequirementMatch m = RequirementMatch.initial(idGenerator.newId(), requirementId,
						cmd.manualMatchStatus(), now2);
				m.override(cmd.manualMatchStatus(), cmd.reason(), now2);
				matchMapper.insert(m);
			} else {
				existing.override(cmd.manualMatchStatus(), cmd.reason(), now2);
				int matchAffected = matchMapper.updateByRequirementIdAndVersion(existing, existing.getVersion());
				VersionCheck.requireAffected(matchAffected, existing.getVersion());
			}
		}

		return requirementMapper.selectById(requirementId);
	}

	@Transactional
	public void deleteRequirement(String requirementId, long expectedVersion) {
		JobRequirement requirement = requirementMapper.selectById(requirementId);
		VersionCheck.requireFound(requirement, "JobRequirement", requirementId);
		if (requirement.getConfirmationStatus() == ConfirmationStatus.CONFIRMED) {
			throw new BusinessRuleException("Confirmed requirement must be restored to PENDING before deletion");
		}
		String now = utcTime.now();
		VersionCheck.requireAffected(requirementMapper.softDelete(requirementId, expectedVersion, now), requirement.getVersion());
		requirementMapper.deleteSkillRefs(requirementId);
		auditLogMapper.insert(AuditLogEntry.requirementChanged(idGenerator.newId(), requirementId,
				"REQUIREMENT_DELETED", "User deleted an unconfirmed requirement candidate.", now));
	}

	/**
	 * 合并同一岗位的重复待确认要求（页面规格 P02：批量操作仅限同类候选项）。
	 * 来源要求软删除并记录 merged_into_requirement_id 指向目标，同时写审计；
	 * 已确认/已忽略的要求不可作为来源，避免破坏既有差距结论。
	 */
	@Transactional
	public JobRequirement merge(String targetRequirementId, List<String> sourceRequirementIds) {
		JobRequirement target = requirementMapper.selectById(targetRequirementId);
		VersionCheck.requireFound(target, "JobRequirement", targetRequirementId);

		Set<String> sourceIds = new LinkedHashSet<>();
		if (sourceRequirementIds != null) {
			for (String id : sourceRequirementIds) {
				if (id != null && !id.isBlank()) {
					sourceIds.add(id.trim());
				}
			}
		}
		if (sourceIds.isEmpty()) {
			throw new BusinessRuleException("At least one source requirement is required to merge");
		}
		if (sourceIds.contains(targetRequirementId)) {
			throw new BusinessRuleException("Target requirement cannot be merged into itself");
		}

		String now = utcTime.now();
		for (String sourceId : sourceIds) {
			JobRequirement source = requirementMapper.selectById(sourceId);
			VersionCheck.requireFound(source, "JobRequirement", sourceId);
			if (!source.getJobId().equals(target.getJobId())) {
				throw new BusinessRuleException("Merged requirements must belong to the same job");
			}
			if (source.getConfirmationStatus() != ConfirmationStatus.PENDING) {
				throw new BusinessRuleException("Only PENDING requirements can be merged");
			}
			if (source.getType() != target.getType()) {
				throw new BusinessRuleException("Merged requirements must have the same type");
			}
		}
		for (String sourceId : sourceIds) {
			requirementMapper.mergeInto(sourceId, targetRequirementId, now);
			auditLogMapper.insert(AuditLogEntry.requirementMerged(idGenerator.newId(), sourceId,
					targetRequirementId, now));
		}
		return requirementMapper.selectById(targetRequirementId);
	}

	private void replaceSkillRefs(JobRequirement requirement, String now) {
		requirementMapper.deleteSkillRefs(requirement.getId());
		String skillId = skillProfileMapper.findActiveSkillIdByNameOrAlias(requirement.getNormalizedName());
		if (skillId != null) {
			requirementMapper.insertSkillRef(requirement.getId(), skillId, now);
		}
	}

	private String textOrCurrent(String value, String current, String message) {
		String result = value == null ? current : value.trim();
		if (result == null || result.isBlank()) {
			throw new BusinessRuleException(message);
		}
		return result;
	}
}
