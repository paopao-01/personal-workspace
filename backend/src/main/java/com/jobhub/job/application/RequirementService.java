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

	public RequirementService(JobMapper jobMapper, JobRequirementMapper requirementMapper,
							   RequirementMatchMapper matchMapper, AuditLogMapper auditLogMapper,
							   IdGenerator idGenerator, UtcTime utcTime, RequirementExtractor extractor) {
		this.jobMapper = jobMapper;
		this.requirementMapper = requirementMapper;
		this.matchMapper = matchMapper;
		this.auditLogMapper = auditLogMapper;
		this.idGenerator = idGenerator;
		this.utcTime = utcTime;
		this.extractor = extractor;
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
		switch (target) {
			case CONFIRMED -> req.confirm(cmd.normalizedName() != null ? cmd.normalizedName() : req.getNormalizedName(),
					cmd.type() != null ? cmd.type() : req.getType(),
					cmd.proficiencyText(), now);
			case IGNORED -> req.ignore(now);
			case PENDING -> req.restoreToPending(now);
		}

		int affected = requirementMapper.updateByIdAndVersion(req, expectedVersion);
		VersionCheck.requireAffected(affected, req.getVersion());
		int bumped = requirementMapper.bumpVersionByIdAndVersion(requirementId, expectedVersion);
		VersionCheck.requireAffected(bumped, req.getVersion());

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
		}
		for (String sourceId : sourceIds) {
			requirementMapper.mergeInto(sourceId, targetRequirementId, now);
			auditLogMapper.insert(AuditLogEntry.requirementMerged(idGenerator.newId(), sourceId,
					targetRequirementId, now));
		}
		return requirementMapper.selectById(targetRequirementId);
	}
}
