package com.jobhub.ai.api;

import com.jobhub.ai.application.AiJobService;
import com.jobhub.ai.domain.AiJob;
import com.jobhub.ai.domain.AiJobItem;
import com.jobhub.ai.domain.AiJobType;
import com.jobhub.ai.domain.AiItemPayload;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AiJobController {
	private final AiJobService service;

	public AiJobController(AiJobService service) {
		this.service = service;
	}

	@PostMapping("/ai-jobs")
	public ResponseEntity<AiJobResponse> create(@Valid @RequestBody AiJobCreateRequest request) {
		return ResponseEntity.status(202)
			.body(AiJobResponse.from(service.create(request.jobType(), request.objectId(), request.sourceText())));
	}

	@PostMapping("/interview-questions/{questionId}/ai-classification")
	public ResponseEntity<AiJobResponse> createQuestionClassification(@PathVariable String questionId) {
		return ResponseEntity.status(202).body(AiJobResponse.from(service.createQuestionClassification(questionId)));
	}

	@GetMapping("/interview-questions/{questionId}/ai-jobs")
	public List<AiJobResponse> listQuestionClassifications(@PathVariable String questionId) {
		return service.listQuestionClassifications(questionId).stream().map(AiJobResponse::from).toList();
	}

	@GetMapping("/ai-jobs/{aiJobId}")
	public AiJobResponse get(@PathVariable String aiJobId) {
		return AiJobResponse.from(service.get(aiJobId));
	}

	@GetMapping("/jobs/{jobId}/ai-jobs")
	public List<AiJobResponse> listByJob(@PathVariable String jobId) {
		return service.listByObject(jobId).stream().map(AiJobResponse::from).toList();
	}

	@PostMapping("/ai-jobs/{aiJobId}/retry")
	public AiJobResponse retry(@PathVariable String aiJobId) {
		return AiJobResponse.from(service.retry(aiJobId));
	}

	@PostMapping("/ai-jobs/{aiJobId}/cancel")
	public AiJobResponse cancel(@PathVariable String aiJobId) {
		return AiJobResponse.from(service.cancel(aiJobId));
	}

	@PostMapping("/ai-job-items/{itemId}/accept")
	public AiJobItemResponse accept(@PathVariable String itemId,
			@RequestHeader(value = "If-Match-Version", required = false) Long questionVersion,
			@RequestBody(required = false) @Valid AiJobItemAcceptRequest request) {
		AiItemPayload edited = request == null ? null : request.payload();
		return AiJobItemResponse.from(service.acceptItem(itemId, edited, questionVersion));
	}

	@PostMapping("/ai-job-items/{itemId}/reject")
	public AiJobItemResponse reject(@PathVariable String itemId) {
		return AiJobItemResponse.from(service.rejectItem(itemId));
	}
}
