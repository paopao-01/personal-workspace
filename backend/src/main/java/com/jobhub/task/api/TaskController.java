package com.jobhub.task.api;

import com.jobhub.task.application.*;
import com.jobhub.task.domain.LearningTask;
import com.jobhub.task.domain.TaskStatus;
import com.jobhub.task.domain.TaskSourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TaskController {
	private final TaskService service;

	public TaskController(TaskService service) {
		this.service = service;
	}

	@GetMapping("/tasks")
	public PageTaskResponse list(@RequestParam(required = false) TaskStatus status,
			@RequestParam(required = false) String knowledgePointId,
			@RequestParam(required = false) TaskSourceType sourceType,
			@RequestParam(required = false) String dueAfter,
			@RequestParam(required = false) String dueBefore,
			@RequestParam(required = false) String jobId,
			@RequestParam(required = false) String interviewId,
			@RequestParam(defaultValue = "1") @Min(1) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
		return PageTaskResponse.from(service.list(new TaskListQuery(status, knowledgePointId, sourceType, dueAfter, dueBefore, jobId, interviewId, page, pageSize)));
	}

	@GetMapping("/tasks/{id}")
	public LearningTaskResponse get(@PathVariable String id) {
		return LearningTaskResponse.from(service.get(id));
	}

	@PostMapping("/tasks")
	public ResponseEntity<LearningTaskResponse> create(@Valid @RequestBody TaskCreateRequest request) {
		LearningTask task = service.create(toCreateCommand(request));
		return ResponseEntity.status(HttpStatus.CREATED).body(LearningTaskResponse.from(task));
	}

	@PutMapping("/tasks/{id}")
	public ResponseEntity<LearningTaskResponse> update(@PathVariable String id,
			@RequestHeader(value = "If-Match-Version", required = false) Long version,
			@Valid @RequestBody TaskUpdateRequest request) {
		if (version == null) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.ok(LearningTaskResponse.from(service.update(id, version,
			new TaskUpdateCommand(request.title(), request.type(), request.knowledgePointIds(),
				request.relatedJobIds(), request.relatedQuestionIds(), request.priority(), request.estimatedMinutes(),
				request.dueAt(), request.learningGoal(), request.acceptanceCriteria(), request.verificationMethod(),
				request.verificationResult(), request.outputUrl()))));
	}

	@PostMapping("/tasks/{id}/transition")
	public ResponseEntity<LearningTaskResponse> transition(@PathVariable String id,
			@RequestHeader(value = "If-Match-Version", required = false) Long version,
			@Valid @RequestBody TaskTransitionRequest request) {
		if (version == null) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.ok(LearningTaskResponse.from(service.transition(id, version,
			new TaskTransitionCommand(request.targetStatus(), request.verificationResult(), request.note()))));
	}

	@PostMapping("/interview-questions/{id}/create-task")
	public ResponseEntity<LearningTaskResponse> createFromQuestion(@PathVariable String id,
			@Valid @RequestBody CreateTaskFromQuestionRequest request) {
		LearningTask task = service.createFromQuestion(id, new CreateTaskFromQuestionCommand(
			request.mode(), request.existingTaskId(), request.title(), request.dueAt(),
			request.acceptanceCriteria(), request.verificationMethod()));
		HttpStatus status = "LINK_EXISTING".equals(request.mode()) ? HttpStatus.OK : HttpStatus.CREATED;
		return ResponseEntity.status(status).body(LearningTaskResponse.from(task));
	}

	private TaskCreateCommand toCreateCommand(TaskCreateRequest request) {
		return new TaskCreateCommand(request.title(), request.type(), request.knowledgePointIds(),
			request.relatedJobIds(), request.relatedQuestionIds(), request.priority(), request.estimatedMinutes(),
			request.dueAt(), request.learningGoal(), request.acceptanceCriteria(), request.verificationMethod(),
			request.outputUrl());
	}
}
