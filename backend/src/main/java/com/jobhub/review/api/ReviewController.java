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
			request.noQuestionsRecorded(), request.overallFeeling(), request.interviewerFocus(), request.jobInterest()));
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
}
