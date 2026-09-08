package com.jobhub.backup.api;

import com.jobhub.backup.application.BackupScheduleService;
import com.jobhub.backup.application.BackupService;
import com.jobhub.backup.domain.BackupRecord;
import com.jobhub.backup.domain.BackupSchedule;
import com.jobhub.datamanagement.api.ImportResultResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
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
	 * 批量清理加密备份：按龄（olderThanDays）或按数量（keepLast）二选一。
	 * olderThanDays：物理删除 created_at 早于「当前 UTC − N 天」的全部备份；
	 * keepLast：保留最近 N 条（created_at DESC），删除其余全部。
	 * 两者互斥，有且仅有一个；复用单条删除联动（删行 + 清文件 + 置空 last_backup_id 软引用）。
	 */
	@DeleteMapping("/backups")
	public ResponseEntity<BackupPurgeSummary> purgeBackups(
			@RequestHeader(value = "X-Confirm-Permanent-Delete", required = false) Boolean confirm,
			@RequestParam(value = "olderThanDays", required = false) @Min(1) Integer olderThanDays,
			@RequestParam(value = "keepLast", required = false) @Min(1) Integer keepLast) {
		if (!Boolean.TRUE.equals(confirm)) {
			return ResponseEntity.badRequest().build();
		}
		// 两个清理条件互斥：有且仅有一个
		if ((olderThanDays == null && keepLast == null) || (olderThanDays != null && keepLast != null)) {
			return ResponseEntity.badRequest().build();
		}
		BackupPurgeSummary summary = olderThanDays != null
			? service.purgeOlderThan(olderThanDays)
			: service.purgeKeepingLast(keepLast);
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

	/**
	 * 分页查询孤儿清理审计日志（只读，仅 action=BACKUP_ORPHAN_CLEANED）。
	 * 不区分独立 clean 与恢复联动来源；只读无需确认头与幂等键；按 occurred_at DESC（最新优先）。
	 */
	@GetMapping("/backups/orphans/audit")
	public PageBackupOrphanAuditEntryResponse listOrphanAudit(
			@RequestParam(defaultValue = "1") @Min(1) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
		long total = service.countOrphanAudit();
		int offset = (page - 1) * pageSize;
		return PageBackupOrphanAuditEntryResponse.from(
				service.listOrphanAudit(pageSize, offset), total, page, pageSize);
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

	/**
	 * 密钥轮换（就地重加密）：用 oldPassphrase 解密既有 .enc 密文 → 用 newPassphrase 重新加密同一明文，
	 * 覆盖原 .enc 文件并就地更新 backup_record 的 salt/iv/size_bytes。备份 id 与明文数据不变。
	 * 非销毁性操作：oldPassphrase 解密成功即授权，无 X-Confirm-Permanent-Delete 确认头；携带 Idempotency-Key。
	 */
	@PostMapping("/backups/{backupId}/rotate-key")
	public BackupRecordResponse rotateKey(@PathVariable String backupId,
			@Valid @RequestBody RotateKeyRequest request) {
		BackupRecord record = service.rotateKey(backupId, request.getOldPassphrase(), request.getNewPassphrase());
		return BackupRecordResponse.from(record);
	}

	/**
	 * 批量密钥轮换（逐条就地重加密）：对一组 backup_record 用同一 oldPassphrase 解密、同一 newPassphrase
	 * 重新加密。逐条独立事务，部分成功不阻塞其他；非销毁性操作，无 X-Confirm-Permanent-Delete（oldPassphrase
	 * 解密成功即授权）；携带 Idempotency-Key。HTTP 200 即使部分或全部失败也 200，摘要反映结果；仅 newPassphrase
	 * 弱返回 400、backupIds 非法返回 400。
	 */
	@PostMapping("/backups/rotate-keys")
	public RotateKeysSummary rotateKeys(@Valid @RequestBody RotateKeysRequest request) {
		return service.rotateKeys(request.getBackupIds(), request.getOldPassphrase(), request.getNewPassphrase());
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
