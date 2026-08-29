package com.jobhub.datamanagement.api;

import com.jobhub.datamanagement.application.TrashService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class TrashController {
	private final TrashService service;

	public TrashController(TrashService service) {
		this.service = service;
	}

	@GetMapping("/trash")
	public List<TrashItemResponse> list() {
		return service.list().stream().map(TrashItemResponse::from).toList();
	}

	@PostMapping("/trash/{trashId}/restore")
	public ResponseEntity<TrashItemResponse> restore(@PathVariable String trashId) {
		return ResponseEntity.ok(TrashItemResponse.from(service.restore(trashId)));
	}

	@DeleteMapping("/trash/{trashId}/permanent")
	public ResponseEntity<Void> purge(@PathVariable String trashId,
			@RequestHeader(value = "X-Confirm-Permanent-Delete", required = false) Boolean confirm) {
		if (!Boolean.TRUE.equals(confirm)) {
			return ResponseEntity.badRequest().build();
		}
		service.purge(trashId);
		return ResponseEntity.noContent().build();
	}
}
