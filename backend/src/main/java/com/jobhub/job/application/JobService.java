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
		long total = jobMapper.selectPageCount(query.query(), query.decisionStatus(), query.jobStatus());
		int offset = (query.page() - 1) * query.pageSize();
		List<Job> items = jobMapper.selectPage(query.query(), query.decisionStatus(), query.jobStatus(),
				query.pageSize(), offset);
		return new JobListResult(items, total, query.page(), query.pageSize());
	}

	@Transactional
	public Job updateJob(String id, long expectedVersion, JobUpdateCommand cmd) {
		Job job = jobMapper.selectById(id);
		VersionCheck.requireFound(job, "Job", id);

		if (cmd.containsBasicInfo()) {
			int affected = jobMapper.updateBasicInfoByIdAndVersion(
					job.updateBasicInfo(cmd.companyName(), cmd.title(), cmd.jdRawText(),
							cmd.source(), cmd.sourceUrl(), cmd.location(), cmd.salaryRange(),
							cmd.notes(), utcTime.now()),
					expectedVersion);
			VersionCheck.requireAffected(affected, expectedVersion);
		}
		if (cmd.containsDecision()) {
			int affected = jobMapper.updateDecisionByIdAndVersion(
					job.updateDecision(cmd.decisionStatus(), cmd.decisionReason(), utcTime.now()),
					expectedVersion);
			VersionCheck.requireAffected(affected, expectedVersion);
		}

		int bumped = jobMapper.bumpVersionByIdAndVersion(id, expectedVersion);
		VersionCheck.requireAffected(bumped, expectedVersion);

		// JD modified → invalidate candidate requirements and gap conclusions
		if (cmd.containsBasicInfo() && cmd.jdRawText() != null && job.jdChanged(cmd.jdRawText())) {
			requirementMapper.markAllPendingByJobId(id, utcTime.now());
			matchMapper.deleteByJobId(id);
		}

		return jobMapper.selectById(id);
	}

	@Transactional
	public Job archive(String id, long expectedVersion) {
		Job job = jobMapper.selectById(id);
		VersionCheck.requireFound(job, "Job", id);
		job.archive(utcTime.now());
		int affected = jobMapper.updateStatusByIdAndVersion(job, expectedVersion);
		VersionCheck.requireAffected(affected, expectedVersion);
		int bumped = jobMapper.bumpVersionByIdAndVersion(id, expectedVersion);
		VersionCheck.requireAffected(bumped, expectedVersion);
		return jobMapper.selectById(id);
	}

	@Transactional
	public Job restore(String id, long expectedVersion) {
		Job job = jobMapper.selectById(id);
		VersionCheck.requireFound(job, "Job", id);
		job.restore(utcTime.now());
		int affected = jobMapper.updateStatusByIdAndVersion(job, expectedVersion);
		VersionCheck.requireAffected(affected, expectedVersion);
		int bumped = jobMapper.bumpVersionByIdAndVersion(id, expectedVersion);
		VersionCheck.requireAffected(bumped, expectedVersion);
		return jobMapper.selectById(id);
	}
}
