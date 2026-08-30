package com.jobhub.application.api;

import com.jobhub.application.application.ApplicationCreateCommand;
import com.jobhub.application.application.ApplicationListQuery;
import com.jobhub.application.application.ApplicationListResult;
import com.jobhub.application.application.ApplicationService;
import com.jobhub.application.application.ApplicationTransitionCommand;
import com.jobhub.application.application.ApplicationUpdateCommand;
import com.jobhub.application.domain.Application;
import com.jobhub.application.domain.ApplicationStatus;
import com.jobhub.application.domain.StatusLogEntry;
import com.jobhub.interview.application.InterviewService;
import com.jobhub.interview.api.InterviewResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 投递端点（6 个）。OpenAPI 契约见 docs/jobhub/03-openapi.yaml。
 *
 * If-Match-Version：缺失返回 400（与 JobController.updateJob/archive/restore 一致）。
 * Idempotency-Key：由 Filter+Interceptor 自动处理，Controller 不感知。
 */
@RestController
@RequestMapping("/api")
public class ApplicationController {

	private final ApplicationService applicationService;
	private final InterviewService interviewService;

	public ApplicationController(ApplicationService applicationService, InterviewService interviewService) {
		this.applicationService = applicationService;
		this.interviewService = interviewService;
	}

	@PostMapping("/applications")
	public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody ApplicationCreateRequest req) {
		Application app = applicationService.create(new ApplicationCreateCommand(
				req.getJobId(), req.getAppliedAt(), req.getChannel(),
				req.getResumeVersion(), req.getExpectedSalary(), req.getContact(),
				req.getNextAction(), req.getNextActionDueAt(), req.isAllowDuplicate(),
				req.getNotes()));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApplicationResponse.from(app));
	}

	@GetMapping("/applications")
	public PageApplicationResponse list(
			@RequestParam(required = false) ApplicationStatus status,
			@RequestParam(required = false) Boolean overdueActionOnly,
			@RequestParam(defaultValue = "1") @Min(1) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
		ApplicationListResult result = applicationService.list(
				new ApplicationListQuery(status, overdueActionOnly, page, pageSize));
		return PageApplicationResponse.from(result.items(), result.total(), page, pageSize);
	}

	@GetMapping("/applications/{applicationId}")
	public ApplicationDetailResponse getDetail(@PathVariable String applicationId) {
		var detail = applicationService.getDetail(applicationId);
		List<InterviewResponse> interviews = interviewService.byApplication(applicationId).stream()
				.map(i -> InterviewResponse.from(i, interviewService.checklist(i.getId())))
				.toList();
		return ApplicationDetailResponse.from(detail, interviews);
	}

	@PutMapping("/applications/{applicationId}")
	public ResponseEntity<ApplicationResponse> update(
			@PathVariable String applicationId,
			@RequestHeader(value = "If-Match-Version", required = false) Long ifMatchVersion,
			@Valid @RequestBody ApplicationUpdateRequest req) {
		if (ifMatchVersion == null) {
			return ResponseEntity.badRequest().build();
		}
		Application updated = applicationService.update(applicationId, ifMatchVersion,
				new ApplicationUpdateCommand(
						req.getChannel(), req.getResumeVersion(), req.getExpectedSalary(),
						req.getContact(), req.getNextAction(), req.getNextActionDueAt(),
						req.getRejectionReason(), req.getNotes()));
		return ResponseEntity.ok(ApplicationResponse.from(updated));
	}

	@DeleteMapping("/applications/{applicationId}")
	public ResponseEntity<Void> delete(@PathVariable String applicationId,
			@RequestHeader(value = "If-Match-Version", required = false) Long ifMatchVersion) {
		if (ifMatchVersion == null) return ResponseEntity.badRequest().build();
		applicationService.delete(applicationId, ifMatchVersion);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/applications/{applicationId}/transition")
	public ResponseEntity<ApplicationResponse> transition(
			@PathVariable String applicationId,
			@RequestHeader(value = "If-Match-Version", required = false) Long ifMatchVersion,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody ApplicationTransitionRequest req) {
		if (ifMatchVersion == null) {
			return ResponseEntity.badRequest().build();
		}
		Application updated = applicationService.transition(applicationId, ifMatchVersion,
				new ApplicationTransitionCommand(req.getTargetStatus(), req.getReason(),
						req.isAllowOfferWithoutCompletedInterview()),
				idempotencyKey);
		return ResponseEntity.ok(ApplicationResponse.from(updated));
	}

	@GetMapping("/applications/{applicationId}/status-history")
	public List<StatusLogResponse> statusHistory(@PathVariable String applicationId) {
		List<StatusLogEntry> logs = applicationService.listStatusHistory(applicationId);
		return StatusLogResponse.fromList(logs);
	}
}
