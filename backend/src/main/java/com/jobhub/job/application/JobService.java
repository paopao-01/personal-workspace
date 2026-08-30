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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JobService {

	private final JobMapper jobMapper;
	private final JobRequirementMapper requirementMapper;
	private final RequirementMatchMapper matchMapper;
	private final IdGenerator idGenerator;
	private final UtcTime utcTime;

	public JobService(JobMapper jobMapper, JobRequirementMapper requirementMapper,
					 RequirementMatchMapper matchMapper, IdGenerator idGenerator, UtcTime utcTime) {
		this.jobMapper = jobMapper;
		this.requirementMapper = requirementMapper;
		this.matchMapper = matchMapper;
		this.idGenerator = idGenerator;
		this.utcTime = utcTime;
	}

	@Transactional
	public Job createJob(JobCreateCommand cmd) {
		String id = idGenerator.newId();
		String now = utcTime.now();
		Job job = Job.create(id, cmd.companyName(), cmd.title(), cmd.jdRawText(), cmd.source(),
				cmd.sourceUrl(), cmd.location(), cmd.salaryRange(), cmd.notes(), now);
		jobMapper.insert(job);
		return jobMapper.selectById(id);
	}

	public Job getJob(String id) {
		Job job = jobMapper.selectById(id);
		VersionCheck.requireFound(job, "Job", id);
		return job;
	}

	public JobListResult listJobs(JobListQuery query) {
		long total = jobMapper.selectPageCount(query.query(), query.decisionStatus(), query.jobStatus(),
				query.location(), query.source(), query.hasPendingRequirements());
		int offset = (query.page() - 1) * query.pageSize();
		List<Job> jobs = jobMapper.selectPage(query.query(), query.decisionStatus(), query.jobStatus(),
				query.location(), query.source(), query.hasPendingRequirements(), query.pageSize(), offset);
		if (jobs.isEmpty()) return new JobListResult(List.of(), total, query.page(), query.pageSize());
		Map<String, JobListMeta> metaByJobId = jobMapper.selectListMetaByIds(jobs.stream().map(Job::getId).toList())
				.stream().collect(Collectors.toMap(JobListMeta::getJobId, Function.identity()));
		List<JobListItem> items = jobs.stream().map(job -> toListItem(job, metaByJobId.get(job.getId()))).toList();
		return new JobListResult(items, total, query.page(), query.pageSize());
	}

	private JobListItem toListItem(Job job, JobListMeta meta) {
		if (meta == null) return new JobListItem(job, 0, 0, "暂无已确认要求", false);
		String gapOverview = meta.getConfirmedRequirementCount() == 0 ? "暂无已确认要求"
				: meta.getNotMetCount() > 0 ? meta.getNotMetCount() + " 项未满足"
				: meta.getInsufficientInfoCount() > 0 ? meta.getInsufficientInfoCount() + " 项信息不足"
				: "已确认要求待补充匹配资料";
		return new JobListItem(job, meta.getConfirmedRequirementCount(), meta.getPendingRequirementCount(),
				gapOverview, meta.isHasActiveApplication());
	}

	@Transactional
	public Job updateJob(String id, long expectedVersion, JobUpdateCommand cmd) {
		Job job = jobMapper.selectById(id);
		VersionCheck.requireFound(job, "Job", id);

		// 在覆盖前快照原 JD，用于判断是否触发要求回退（updateBasicInfo 会把 job.jdRawText 改为新值）
		String originalJdRawText = job.getJdRawText();
		boolean originalJdChanged = cmd.jdRawText() != null && job.jdChanged(cmd.jdRawText());

		if (cmd.containsBasicInfo()) {
			int affected = jobMapper.updateBasicInfoByIdAndVersion(
					job.updateBasicInfo(cmd.companyName(), cmd.title(), cmd.jdRawText(),
							cmd.source(), cmd.sourceUrl(), cmd.location(), cmd.salaryRange(),
							cmd.notes(), utcTime.now()),
					expectedVersion);
			VersionCheck.requireAffected(affected, job.getVersion());
		}
		if (cmd.containsDecision()) {
			int affected = jobMapper.updateDecisionByIdAndVersion(
					job.updateDecision(cmd.decisionStatus(), cmd.decisionReason(), utcTime.now()),
					expectedVersion);
			VersionCheck.requireAffected(affected, job.getVersion());
		}

		int bumped = jobMapper.bumpVersionByIdAndVersion(id, expectedVersion);
		VersionCheck.requireAffected(bumped, job.getVersion());

		// JD modified → invalidate candidate requirements and gap conclusions（02-state-machines.md 2.2）
		// 必须基于更新后的 JD 判断：job 对象在 updateBasicInfo 后已持有新 jdRawText，
		// 与传入 cmd.jdRawText() 相同故 jdChanged 必为 false。改用更新前快照与 cmd 对比。
		if (cmd.containsBasicInfo() && cmd.jdRawText() != null && originalJdChanged) {
			requirementMapper.markAllPendingByJobId(id, utcTime.now());
			matchMapper.invalidateByJobId(id, utcTime.now());
		}

		return jobMapper.selectById(id);
	}

	@Transactional
	public Job archive(String id, long expectedVersion) {
		Job job = jobMapper.selectById(id);
		VersionCheck.requireFound(job, "Job", id);
		job.archive(utcTime.now());
		int affected = jobMapper.updateStatusByIdAndVersion(job, expectedVersion);
		VersionCheck.requireAffected(affected, job.getVersion());
		int bumped = jobMapper.bumpVersionByIdAndVersion(id, expectedVersion);
		VersionCheck.requireAffected(bumped, job.getVersion());
		return jobMapper.selectById(id);
	}

	@Transactional
	public Job restore(String id, long expectedVersion) {
		Job job = jobMapper.selectById(id);
		VersionCheck.requireFound(job, "Job", id);
		job.restore(utcTime.now());
		int affected = jobMapper.updateStatusByIdAndVersion(job, expectedVersion);
		VersionCheck.requireAffected(affected, job.getVersion());
		int bumped = jobMapper.bumpVersionByIdAndVersion(id, expectedVersion);
		VersionCheck.requireAffected(bumped, job.getVersion());
		return jobMapper.selectById(id);
	}
}
