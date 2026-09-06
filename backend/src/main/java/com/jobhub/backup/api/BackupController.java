package com.jobhub.backup.api;

import com.jobhub.backup.application.BackupService;
import com.jobhub.backup.domain.BackupRecord;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 加密备份 REST 接口：生成、列表、下载。备份为追加型只读历史。
 */
@RestController
@RequestMapping("/api")
public class BackupController {
	private final BackupService service;

	public BackupController(BackupService service) {
		this.service = service;
	}

	@PostMapping("/backups")
	public ResponseEntity<BackupRecordResponse> create(@Valid @RequestBody BackupCreateRequest request) {
		BackupRecord record = service.create(request.passphrase());
		return ResponseEntity.status(HttpStatus.CREATED).body(BackupRecordResponse.from(record));
	}

	@GetMapping("/backups")
	public List<BackupRecordResponse> list() {
		return service.list().stream().map(BackupRecordResponse::from).toList();
	}

	@GetMapping("/backups/{backupId}/download")
	public ResponseEntity<byte[]> download(@PathVariable String backupId) {
		BackupRecord record = service.get(backupId);
		byte[] content = service.readFileBytes(record);
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + record.getFileName())
			.contentType(MediaType.APPLICATION_OCTET_STREAM)
			.body(content);
	}
}
