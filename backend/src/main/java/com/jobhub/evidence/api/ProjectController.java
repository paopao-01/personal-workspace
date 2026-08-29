package com.jobhub.evidence.api;

import com.jobhub.evidence.application.ProjectCreateCommand;
import com.jobhub.evidence.application.ProjectService;
import com.jobhub.evidence.domain.ProjectCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ProjectController {
	private final ProjectService service;

	public ProjectController(ProjectService service) {
		this.service = service;
	}

	@GetMapping("/projects")
	public List<ProjectCaseSummaryResponse> list() {
		return service.list().stream().map(ProjectCaseSummaryResponse::from).toList();
	}

	@PostMapping("/projects")
	public ResponseEntity<ProjectCaseSummaryResponse> create(@Valid @RequestBody ProjectCaseCreateRequest request) {
		ProjectCase project = service.create(toCommand(request));
		return ResponseEntity.status(HttpStatus.CREATED).body(ProjectCaseSummaryResponse.from(project));
	}

	@PutMapping("/projects/{projectId}")
	public ResponseEntity<ProjectCaseSummaryResponse> update(@PathVariable String projectId,
			@RequestHeader(value = "If-Match-Version", required = false) Long version,
			@Valid @RequestBody ProjectCaseCreateRequest request) {
		if (version == null) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.ok(ProjectCaseSummaryResponse.from(service.update(projectId, version, toCommand(request))));
	}

	@DeleteMapping("/projects/{projectId}")
	public ResponseEntity<Void> delete(@PathVariable String projectId,
			@RequestHeader(value = "If-Match-Version", required = false) Long version) {
		if (version == null) {
			return ResponseEntity.badRequest().build();
		}
		service.delete(projectId, version);
		return ResponseEntity.noContent().build();
	}

	private ProjectCreateCommand toCommand(ProjectCaseCreateRequest request) {
		return new ProjectCreateCommand(request.title(), request.scenario(), request.approach(),
			request.problemSolved(), request.result(), request.evidenceIds());
	}
}
