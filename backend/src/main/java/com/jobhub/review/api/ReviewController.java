package com.jobhub.review.api;

import com.jobhub.review.application.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ReviewController {
	private final ReviewService service;

	public ReviewController(ReviewService service) {
		this.service = service;
	}

	@GetMapping("/interviews/{id}/review")
	public InterviewReviewResponse getByInterview(@PathVariable String id) {
		return InterviewReviewResponse.from(service.getByInterview(id));
	}

	@PutMapping("/interviews/{id}/review")
	public InterviewReviewResponse saveDraft(@PathVariable String id,
			@RequestHeader(value = "If-Match-Version", required = false) Long version,
			@Valid @RequestBody ReviewUpsertRequest request) {
		return InterviewReviewResponse.from(service.saveDraft(id, version, request.interviewResult(),
			request.noQuestionsRecorded(), request.overallFeeling(), request.interviewerFocus(), request.jobInterest(),
			request.projectExpressRisk()));
	}

	@PostMapping("/reviews/{id}/questions")
	public ResponseEntity<InterviewQuestionResponse> addQuestion(@PathVariable String id,
			@Valid @RequestBody QuestionCreateRequest request) {
		List<String> knowledgePointIds = request.knowledgePointIds() == null ? List.of() : request.knowledgePointIds();
		return ResponseEntity.status(HttpStatus.CREATED).body(InterviewQuestionResponse.from(
			service.addQuestion(id, request.content(), request.answerStatus(), request.type(), knowledgePointIds)));
	}

	@PostMapping("/reviews/{id}/complete")
	public ResponseEntity<InterviewReviewResponse> complete(@PathVariable String id,
			@RequestHeader(value = "If-Match-Version", required = false) Long version) {
		if (version == null) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.ok(InterviewReviewResponse.from(service.complete(id, version)));
	}

	@PostMapping("/reviews/{id}/reopen")
	public ResponseEntity<InterviewReviewResponse> reopen(@PathVariable String id,
			@RequestHeader(value = "If-Match-Version", required = false) Long version) {
		if (version == null) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.ok(InterviewReviewResponse.from(service.reopen(id, version)));
	}

	@PutMapping("/interview-questions/{id}")
	public InterviewQuestionResponse updateQuestion(@PathVariable String id,
			@RequestHeader(value = "If-Match-Version", required = false) Long version,
			@Valid @RequestBody QuestionUpdateRequest request) {
		if (version == null) {
			throw new com.jobhub.common.error.BusinessRuleException("If-Match-Version is required");
		}
		List<String> knowledgePointIds = request.knowledgePointIds() == null ? List.of() : request.knowledgePointIds();
		return InterviewQuestionResponse.from(service.updateQuestion(id, version, request.content(),
			request.answerStatus(), request.type(), knowledgePointIds, request.myAnswer(), request.referenceAnswer(),
			request.difficulty(), request.errorReason(), request.improvementPlan()));
	}

	@DeleteMapping("/interview-questions/{id}")
	public ResponseEntity<Void> deleteQuestion(@PathVariable String id,
			@RequestHeader(value = "If-Match-Version", required = false) Long version) {
		if (version == null) {
			return ResponseEntity.badRequest().build();
		}
		service.deleteQuestion(id, version);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/knowledge-points")
	public List<KnowledgePointResponse> listKnowledgePoints(@RequestParam(value = "query", required = false) String query) {
		return service.listKnowledgePoints(query).stream().map(KnowledgePointResponse::from).toList();
	}

	@PostMapping("/knowledge-points")
	public ResponseEntity<KnowledgePointResponse> createKnowledgePoint(@Valid @RequestBody KnowledgePointCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(KnowledgePointResponse.from(service.createKnowledgePoint(request.name(), request.category())));
	}

	@GetMapping("/reviews/analysis")
	public ReviewAnalysisResponse analysis(
			@RequestParam(value = "from", required = false) String from,
			@RequestParam(value = "to", required = false) String to,
			@RequestParam(value = "jobId", required = false) String jobId) {
		return ReviewAnalysisResponse.from(service.analysis(from, to, jobId));
	}

	@GetMapping("/knowledge-points/weak")
	public List<WeakKnowledgePointResponse> weakKnowledgePoints(
			@RequestParam(value = "from", required = false) String from,
			@RequestParam(value = "to", required = false) String to,
			@RequestParam(value = "jobId", required = false) String jobId) {
		return service.weakKnowledgePoints(from, to, jobId).stream().map(WeakKnowledgePointResponse::from).toList();
	}
}
