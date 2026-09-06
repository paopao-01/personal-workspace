package com.jobhub.backup.api;

import com.jobhub.backup.application.BackupScheduleService;
import com.jobhub.backup.application.BackupService;
import com.jobhub.backup.domain.BackupRecord;
import com.jobhub.backup.domain.BackupSchedule;
import com.jobhub.datamanagement.api.ImportResultResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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
 * 加密备份 REST 接口：生成、列表、下载、恢复，以及定时备份调度配置与武装。
 * 备份为追加型只读历史；调度配置为单行可变元数据；passphrase 永不持久化。
 */
@RestController
@RequestMapping("/api")
@Validated
public class BackupController {
	private final BackupService service;
	private final BackupScheduleService scheduleService;

	public BackupController(BackupService service, BackupScheduleService scheduleService) {
		this.service = service;
		this.scheduleService = scheduleService;
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

	/**
	 * 按龄批量清理：物理删除 created_at 早于「当前 UTC − olderThanDays 天」的全部备份与 .enc 文件。
	 * 复用单条删除联动（删行 + 清文件 + 置空 last_backup_id 软引用）。返回清理摘要。
	 */
	@DeleteMapping("/backups")
	public ResponseEntity<BackupPurgeSummary> purgeOlderThan(
			@RequestHeader(value = "X-Confirm-Permanent-Delete", required = false) Boolean confirm,
			@RequestParam(value = "olderThanDays", required = false) @Min(1) Integer olderThanDays) {
		if (!Boolean.TRUE.equals(confirm)) {
			return ResponseEntity.badRequest().build();
		}
		if (olderThanDays == null) {
			return ResponseEntity.badRequest().build();
		}
		BackupPurgeSummary summary = service.purgeOlderThan(olderThanDays);
		return ResponseEntity.ok(summary);
	}

	/**
	 * 扫描并清理孤儿 .enc 密文文件：物理删除 backup-dir 下无 backup_record 对应的 .enc 文件。
	 * 不写记录、不联动 last_backup_id/data_export；passphrase 不参与清理验证。要求 X-Confirm-Permanent-Delete 确认头。
	 */
	@PostMapping("/backups/orphans/clean")
	public ResponseEntity<BackupOrphanCleanSummary> cleanOrphans(
			@RequestHeader(value = "X-Confirm-Permanent-Delete", required = false) Boolean confirm) {
		if (!Boolean.TRUE.equals(confirm)) {
			return ResponseEntity.badRequest().build();
		}
		BackupOrphanCleanSummary summary = service.cleanOrphans();
		return ResponseEntity.ok(summary);
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

	@DeleteMapping("/backups/{backupId}")
	public ResponseEntity<Void> delete(@PathVariable String backupId,
			@RequestHeader(value = "X-Confirm-Permanent-Delete", required = false) Boolean confirm) {
		if (!Boolean.TRUE.equals(confirm)) {
			return ResponseEntity.badRequest().build();
		}
		service.delete(backupId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping(
		value = "/backups/restore",
		consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ImportResultResponse restore(
			@RequestPart("file") @NotNull MultipartFile file,
			@RequestPart("passphrase") @NotBlank @Size(min = 8, max = 256) String passphrase) {
		return service.restore(file, passphrase);
	}

	@GetMapping("/backups/schedule")
	public BackupScheduleResponse getSchedule() {
		BackupSchedule schedule = scheduleService.get();
		return BackupScheduleResponse.from(schedule, scheduleService.isArmed());
	}

	@PutMapping("/backups/schedule")
	public ResponseEntity<BackupScheduleResponse> updateSchedule(
			@RequestHeader(value = "If-Match-Version", required = false) Long ifMatchVersion,
			@Valid @RequestBody BackupScheduleUpdateRequest request) {
		if (ifMatchVersion == null) {
			return ResponseEntity.badRequest().build();
		}
		BackupSchedule schedule = scheduleService.update(
				ifMatchVersion, request.getCronExpression(), request.getEnabled());
		return ResponseEntity.ok(BackupScheduleResponse.from(schedule, scheduleService.isArmed()));
	}

	@PostMapping("/backups/schedule/arm")
	public BackupScheduleResponse arm(@Valid @RequestBody BackupArmRequest request) {
		BackupSchedule schedule = scheduleService.arm(request.getPassphrase());
		return BackupScheduleResponse.from(schedule, scheduleService.isArmed());
	}
}
