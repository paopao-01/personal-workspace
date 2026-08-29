package com.jobhub.datamanagement.api;

import com.jobhub.datamanagement.application.ExportService;
import com.jobhub.datamanagement.domain.DataExport;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ExportController {
	private final ExportService service;

	public ExportController(ExportService service) {
		this.service = service;
	}

	@PostMapping("/data-exports")
	public ResponseEntity<DataExportResponse> create(@Valid @RequestBody ExportCreateRequest request) {
		DataExport export = service.create(request.format());
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(DataExportResponse.from(export));
	}

	@GetMapping("/data-exports/{exportId}")
	public DataExportResponse get(@PathVariable String exportId) {
		return DataExportResponse.from(service.get(exportId));
	}

	@GetMapping("/data-exports/{exportId}/download")
	public ResponseEntity<byte[]> download(@PathVariable String exportId) {
		byte[] content = service.readExportFile(service.get(exportId));
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=jobhub-export-" + exportId + ".json")
			.contentType(MediaType.APPLICATION_JSON)
			.body(content);
	}
}
