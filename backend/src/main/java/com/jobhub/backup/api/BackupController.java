package com.jobhub.backup.api;

import com.jobhub.backup.application.BackupService;
import com.jobhub.backup.domain.BackupRecord;
import com.jobhub.datamanagement.api.ImportResultResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 加密备份 REST 接口：生成、列表、下载、恢复。备份为追加型只读历史。
 */
@RestController
@RequestMapping("/api")
@Validated
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

	@PostMapping(
		value = "/backups/restore",
		consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ImportResultResponse restore(
			@RequestPart("file") @NotNull MultipartFile file,
			@RequestPart("passphrase") @NotBlank @Size(min = 8, max = 256) String passphrase) {
		return service.restore(file, passphrase);
	}
}
