package com.jobhub.job.application;

import com.jobhub.common.version.VersionCheck;
import com.jobhub.job.domain.*;
import com.jobhub.job.infrastructure.JobMapper;
import com.jobhub.job.infrastructure.JobRequirementMapper;
import com.jobhub.job.infrastructure.RequirementMatchMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 差距清单服务。仅基于 CONFIRMED 要求生成结论（02-state-machines.md 2.2、AT-02）。
 *
	 * 人工修正优先于规则；否则只依据用户明确填写的自评和关联证据计算，资料不足保持 INSUFFICIENT_INFO。
 */
@Service
public class GapListService {

	private final JobMapper jobMapper;
	private final JobRequirementMapper requirementMapper;
	private final RequirementMatchMapper matchMapper;

	public GapListService(JobMapper jobMapper, JobRequirementMapper requirementMapper,
						  RequirementMatchMapper matchMapper) {
		this.jobMapper = jobMapper;
		this.requirementMapper = requirementMapper;
		this.matchMapper = matchMapper;
	}

	public List<GapItem> getGapList(String jobId) {
		Job job = jobMapper.selectById(jobId);
		VersionCheck.requireFound(job, "Job", jobId);

		List<JobRequirement> confirmed = requirementMapper.selectConfirmedByJobId(jobId);
		if (confirmed.isEmpty()) {
			return List.of();
		}

		List<String> reqIds = confirmed.stream().map(JobRequirement::getId).toList();
		List<RequirementMatch> matches = matchMapper.selectByRequirementIds(reqIds);
		Map<String, RequirementMatch> byReqId = new HashMap<>();
		for (RequirementMatch m : matches) {
			byReqId.put(m.getRequirementId(), m);
		}

		List<GapItem> items = new ArrayList<>();
		for (JobRequirement req : confirmed) {
			RequirementMatch match = byReqId.get(req.getId());
			GapStatus status;
			String reason = null;
			List<GapEvidence> evidence = requirementMapper.selectActiveEvidence(req.getId());
			if (match != null) {
				status = match.getMatchStatus();
				reason = match.getManualOverrideReason();
			} else {
				status = calculate(req.getId());
			}
			items.add(new GapItem(req, status, evidence, reason));
		}
		return items;
	}

	private GapStatus calculate(String requirementId) {
		List<RequirementSkillFact> facts = requirementMapper.selectSkillFacts(requirementId);
		if (facts.isEmpty()) return GapStatus.INSUFFICIENT_INFO;
		if (facts.stream().anyMatch(fact -> fact.getEvidenceCount() > 0)) {
			return GapStatus.SATISFIED_WITH_EVIDENCE;
		}
		if (facts.stream().anyMatch(fact -> fact.getSelfLevel() != null && fact.getSelfLevel() > 0)) {
			return GapStatus.SELF_REPORTED_NO_EVIDENCE;
		}
		if (facts.stream().allMatch(fact -> fact.getSelfLevel() != null && fact.getSelfLevel() == 0)) {
			return GapStatus.NOT_MET;
		}
		return GapStatus.INSUFFICIENT_INFO;
	}
}
