package com.jobhub.job.application;

import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.common.version.VersionCheck;
import com.jobhub.job.domain.*;
import com.jobhub.job.infrastructure.JobMapper;
import com.jobhub.job.infrastructure.JobRequirementMapper;
import com.jobhub.job.infrastructure.RequirementMatchMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RequirementService {

	private final JobMapper jobMapper;
	private final JobRequirementMapper requirementMapper;
	private final RequirementMatchMapper matchMapper;
	private final IdGenerator idGenerator;
	private final UtcTime utcTime;
	private final RequirementExtractor extractor;

	public RequirementService(JobMapper jobMapper, JobRequirementMapper requirementMapper,
							   RequirementMatchMapper matchMapper, IdGenerator idGenerator,
							   UtcTime utcTime, RequirementExtractor extractor) {
		this.jobMapper = jobMapper;
		this.requirementMapper = requirementMapper;
		this.matchMapper = matchMapper;
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
}
