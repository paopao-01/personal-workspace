package com.jobhub.job.api;

import com.jobhub.job.application.RequirementService;
import com.jobhub.job.application.RequirementUpdateCommand;
import com.jobhub.job.domain.JobRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class JobRequirementController {

	private final RequirementService requirementService;

	public JobRequirementController(RequirementService requirementService) {
		this.requirementService = requirementService;
	}

	@PutMapping("/job-requirements/{requirementId}")
	public ResponseEntity<JobRequirementResponse> update(
			@PathVariable String requirementId,
			@RequestHeader(value = "If-Match-Version", required = false) Long ifMatchVersion,
			@Valid @RequestBody RequirementUpdateRequest req) {
		if (ifMatchVersion == null) {
			return ResponseEntity.badRequest().build();
		}
		JobRequirement updated = requirementService.updateRequirement(requirementId, ifMatchVersion,
				new RequirementUpdateCommand(
						req.getConfirmationStatus(),
						req.getRawText(),
						req.getNormalizedName(),
						req.getType(),
						req.getProficiencyText(),
						req.getReason(),
						req.getManualMatchStatus()));
		return ResponseEntity.ok(JobRequirementResponse.from(updated));
	}

	@PostMapping("/job-requirements/merge")
	public ResponseEntity<JobRequirementResponse> merge(@Valid @RequestBody RequirementMergeRequest request) {
		JobRequirement target = requirementService.merge(
				request.targetRequirementId(), request.sourceRequirementIds());
		return ResponseEntity.ok(JobRequirementResponse.from(target));
	}
}
