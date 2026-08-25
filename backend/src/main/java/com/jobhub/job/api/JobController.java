package com.jobhub.job.api;

import com.jobhub.common.time.UtcTime;
import com.jobhub.job.application.ExtractionResult;
import com.jobhub.job.application.GapItem;
import com.jobhub.job.application.JobCreateCommand;
import com.jobhub.job.application.JobListQuery;
import com.jobhub.job.application.JobListResult;
import com.jobhub.job.application.JobService;
import com.jobhub.job.application.JobUpdateCommand;
import com.jobhub.job.application.RequirementService;
import com.jobhub.job.application.RequirementUpdateCommand;
import com.jobhub.job.domain.Job;
import com.jobhub.job.domain.JobDecisionStatus;
import com.jobhub.job.domain.JobStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class JobController {

	private final JobService jobService;
	private final RequirementService requirementService;
	private final com.jobhub.job.application.GapListService gapListService;
	private final UtcTime utcTime;

	public JobController(JobService jobService, RequirementService requirementService,
						com.jobhub.job.application.GapListService gapListService, UtcTime utcTime) {
		this.jobService = jobService;
		this.requirementService = requirementService;
		this.gapListService = gapListService;
		this.utcTime = utcTime;
	}

	@PostMapping("/jobs")
	public ResponseEntity<JobResponse> createJob(@Valid @RequestBody JobCreateRequest req) {
		Job job = jobService.createJob(new JobCreateCommand(
				req.getCompanyName(), req.getTitle(), req.getJdRawText(),
				req.getSource(), req.getSourceUrl(), req.getLocation(),
				req.getSalaryRange(), req.getNotes()));
		return ResponseEntity.status(HttpStatus.CREATED).body(JobResponse.from(job));
	}

	@GetMapping("/jobs")
	public PageJobResponse listJobs(
			@RequestParam(required = false) String query,
			@RequestParam(required = false) JobDecisionStatus decisionStatus,
			@RequestParam(required = false) JobStatus jobStatus,
			@RequestParam(defaultValue = "1") @Min(1) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
		JobListResult result = jobService.listJobs(new JobListQuery(query, decisionStatus, jobStatus, page, pageSize));
		return PageJobResponse.from(result.items(), result.total(), page, pageSize);
	}

	@GetMapping("/jobs/{jobId}")
	public JobResponse getJob(@PathVariable String jobId) {
		return JobResponse.from(jobService.getJob(jobId));
	}

	@PutMapping("/jobs/{jobId}")
	public ResponseEntity<JobResponse> updateJob(
			@PathVariable String jobId,
			@RequestHeader(value = "If-Match-Version", required = false) Long ifMatchVersion,
			@Valid @RequestBody JobUpdateRequest req) {
		if (ifMatchVersion == null) {
			return ResponseEntity.badRequest().build();
		}
		Job updated = jobService.updateJob(jobId, ifMatchVersion, new JobUpdateCommand(
				req.getCompanyName(), req.getTitle(), req.getJdRawText(), req.getSource(),
				req.getSourceUrl(), req.getLocation(), req.getSalaryRange(), req.getNotes(),
				req.getDecisionStatus(), req.getDecisionReason()));
		return ResponseEntity.ok(JobResponse.from(updated));
	}

	@PostMapping("/jobs/{jobId}/archive")
	public ResponseEntity<JobResponse> archive(
			@PathVariable String jobId,
			@RequestHeader(value = "If-Match-Version", required = false) Long ifMatchVersion) {
		if (ifMatchVersion == null) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.ok(JobResponse.from(jobService.archive(jobId, ifMatchVersion)));
	}

	@PostMapping("/jobs/{jobId}/restore")
	public ResponseEntity<JobResponse> restore(
			@PathVariable String jobId,
			@RequestHeader(value = "If-Match-Version", required = false) Long ifMatchVersion) {
		if (ifMatchVersion == null) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.ok(JobResponse.from(jobService.restore(jobId, ifMatchVersion)));
	}

	@PostMapping("/jobs/{jobId}/requirements/extract")
	public RequirementExtractionResultResponse extract(@PathVariable String jobId) {
		ExtractionResult result = requirementService.extractRequirements(jobId);
		return RequirementExtractionResultResponse.from(result, jobId, utcTime.now());
	}

	@GetMapping("/jobs/{jobId}/requirements")
	public List<JobRequirementResponse> listRequirements(@PathVariable String jobId) {
		return requirementService.listByJob(jobId).stream()
				.map(JobRequirementResponse::from)
				.toList();
	}

	@GetMapping("/jobs/{jobId}/gap-list")
	public List<GapItemResponse> getGapList(@PathVariable String jobId) {
		List<GapItem> items = gapListService.getGapList(jobId);
		return items.stream().map(GapItemResponse::from).toList();
	}
}
