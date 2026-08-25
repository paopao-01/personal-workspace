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
 * P0 本切片简化：因 user_skill/evidence 在本切片尚未录入，所有 CONFIRMED 要求默认 INSUFFICIENT_INFO。
 * 若存在人工修正的 requirement_match（manual_override_reason + match_status），则使用修正值（AT-04）。
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
			if (match != null) {
				status = match.getMatchStatus();
				reason = match.getManualOverrideReason();
			} else {
				// 无 user_skill 资料时默认 INSUFFICIENT_INFO（AT-01 验收依据）
				status = GapStatus.INSUFFICIENT_INFO;
			}
			items.add(new GapItem(req, status, reason));
		}
		return items;
	}
}
