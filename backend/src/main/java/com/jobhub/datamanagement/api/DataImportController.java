package com.jobhub.datamanagement.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobhub.datamanagement.application.ImportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DataImportController {
	private final ImportService service;

	public DataImportController(ImportService service) {
		this.service = service;
	}

	@PostMapping("/data-imports/validate")
	public ImportValidationResponse validate(@RequestBody JsonNode body) {
		return service.validate(body);
	}

	@PostMapping("/data-imports/restore")
	public ImportResultResponse restore(@RequestBody JsonNode body) {
		return service.restore(body);
	}
}
