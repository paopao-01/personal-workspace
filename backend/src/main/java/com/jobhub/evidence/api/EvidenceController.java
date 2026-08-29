package com.jobhub.evidence.api;

import com.jobhub.evidence.application.EvidenceCreateCommand;
import com.jobhub.evidence.application.EvidenceService;
import com.jobhub.evidence.domain.Evidence;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class EvidenceController {
	private final EvidenceService service;

	public EvidenceController(EvidenceService service) {
		this.service = service;
	}

	@GetMapping("/evidence")
	public List<EvidenceResponse> list() {
		return service.list().stream().map(EvidenceResponse::from).toList();
	}

	@PostMapping("/evidence")
	public ResponseEntity<EvidenceResponse> create(@Valid @RequestBody EvidenceCreateRequest request) {
		Evidence evidence = service.create(toCommand(request));
		return ResponseEntity.status(HttpStatus.CREATED).body(EvidenceResponse.from(evidence));
	}

	@PutMapping("/evidence/{evidenceId}")
	public ResponseEntity<EvidenceResponse> update(@PathVariable String evidenceId,
			@RequestHeader(value = "If-Match-Version", required = false) Long version,
			@Valid @RequestBody EvidenceCreateRequest request) {
		if (version == null) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.ok(EvidenceResponse.from(service.update(evidenceId, version, toCommand(request))));
	}

	private EvidenceCreateCommand toCommand(EvidenceCreateRequest request) {
		return new EvidenceCreateCommand(request.type(), request.title(), request.whereUsed(),
			request.problemSolved(), request.approach(), request.result(), request.urlOrPath(), request.skillIds());
	}
}
