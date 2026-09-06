# 加密备份（手动触发）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: 用 superpowers:executing-plans 逐任务实现。步骤用 checkbox（`- [ ]`）跟踪。

**Goal:** 用户在设置页输入 passphrase 后一键生成 AES-256-GCM 加密数据备份，元数据落库可列表与下载。

**Architecture:** 新增 `com.jobhub.backup` 模块；后端复用 `ExportService` 生成标准数据包 → `EncryptionService`（PBKDF2+AES-GCM）加密 → 落盘 `./data/backups/<id>.enc` → `BackupRecordMapper` 记录；前端在设置页加区块。

**Tech Stack:** Spring Boot 3.3.5 / Java 21 `javax.crypto`、MyBatis、Flyway V24、React+TanStack Query、openapi-typescript。

**Spec:** `docs/superpowers/specs/2026-09-06-encrypted-backup-design.md`

## 全局约束

- 本地单用户，只监听 127.0.0.1；无登录/云同步。
- 写操作幂等（`Idempotency-Key`）；备份为追加型只读历史。
- UTC 保存时间，UI 按用户时区显示。
- passphrase 仅写入不回显，永不落盘/日志；salt+iv 落库。
- 已执行 Flyway 迁移不可改，结构变更新增 V24。
- 错误响应含稳定错误码。

---

### Task 1: V24 迁移与数据库设计文档

**Files:**
- Create: `backend/src/main/resources/db/migration/V24__create_backup_record.sql`
- Modify: `docs/jobhub/04-database-design.md`

- [ ] **Step 1: 写 V24 迁移**

```sql
CREATE TABLE backup_record (
  id                VARCHAR(36) PRIMARY KEY,
  created_at        VARCHAR(32) NOT NULL,
  algorithm         VARCHAR(32) NOT NULL,
  pbkdf2_iterations INTEGER NOT NULL,
  salt              BLOB NOT NULL,
  iv                BLOB NOT NULL,
  data_export_id    VARCHAR(36) NOT NULL,
  file_path         VARCHAR(512) NOT NULL,
  file_name         VARCHAR(256) NOT NULL,
  size_bytes        INTEGER NOT NULL
);
CREATE INDEX idx_backup_record_created_at ON backup_record(created_at);
```

- [ ] **Step 2: 在 04-database-design.md 补 backup_record 表语义**

在数据表清单新增 `backup_record` 节：纯追加只读历史，无状态/版本；salt+iv 落库供未来恢复派生密钥；data_export_id 软引用无外键；不存 passphrase/派生密钥。

- [ ] **Step 3: 启动验证 Flyway V24 成功**

Run: `cd backend && mvn -q test -Dtest=BackupIntegrationTest#placeholder -DfailIfNoTests=false 2>&1 | grep -i flyway || true`
（此步仅确认迁移语法；下一任务才有测试。可用 `mvn -q spring-boot:validate` 或直接在 Task 3 集成测试启动时验证 V24。）

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/V24__create_backup_record.sql docs/jobhub/04-database-design.md
git commit -m "feat(backup): add backup_record table V24"
```

---

### Task 2: EncryptionService（纯加密工具，TDD）

**Files:**
- Create: `backend/src/main/java/com/jobhub/backup/application/EncryptionService.java`
- Test: `backend/src/test/java/com/jobhub/backup/EncryptionServiceTest.java`

**Interfaces:**
- Produces: `EncryptionService`，方法 `EncryptedPayload encrypt(byte[] plaintext, String passphrase)` 与 `byte[] decrypt(EncryptedPayload payload, String passphrase)`；`EncryptedPayload` 为内嵌 record `EncryptedPayload(byte[] salt, byte[] iv, byte[] ciphertext)`。

- [ ] **Step 1: 写失败测试**

```java
package com.jobhub.backup;

import com.jobhub.backup.application.EncryptionService;
import com.jobhub.backup.application.EncryptionService.EncryptedPayload;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {
	private final EncryptionService enc = new EncryptionService();

	@Test
	void encryptThenDecryptRoundTrip() {
		byte[] plain = "hello 备份".getBytes(StandardCharsets.UTF_8);
		EncryptedPayload payload = enc.encrypt(plain, "test1234");
		assertArrayEquals(plain, enc.decrypt(payload, "test1234"));
	}

	@Test
	void differentSaltsProduceDifferentCiphertext() {
		byte[] plain = "data".getBytes(StandardCharsets.UTF_8);
		EncryptedPayload a = enc.encrypt(plain, "pw");
		EncryptedPayload b = enc.encrypt(plain, "pw");
		assertNotEquals(16, 0); // sanity
		assertFalse(java.util.Arrays.equals(a.ciphertext(), b.ciphertext()));
	}

	@Test
	void wrongPassphraseFailsToDecrypt() {
		byte[] plain = "data".getBytes(StandardCharsets.UTF_8);
		EncryptedPayload payload = enc.encrypt(plain, "correct");
		assertThrows(Exception.class, () -> enc.decrypt(payload, "wrong"));
	}

	@Test
	void saltAndIvHaveExpectedLength() {
		EncryptedPayload p = enc.encrypt(new byte[]{1}, "x");
		assertEquals(16, p.salt().length);
		assertEquals(12, p.iv().length);
	}
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && mvn -q test -Dtest=EncryptionServiceTest 2>&1 | tail -5`
Expected: 编译失败（EncryptionService 不存在）。

- [ ] **Step 3: 写实现**

```java
package com.jobhub.backup.application;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * AES-256-GCM 加密 + PBKDF2WithHmacSHA256 密钥派生。
 * passphrase 与派生密钥永不持久化；salt 与 iv 随记录落库以供恢复。
 */
@Component
public class EncryptionService {
	static final int SALT_BYTES = 16;
	static final int IV_BYTES = 12;
	static final int KEY_BITS = 256;
	static final int ITERATIONS = 100_000;
	static final int TAG_BITS = 128;
	private static final String KDF = "PBKDF2WithHmacSHA256";
	private static final String CIPHER = "AES/GCM/NoPadding";

	public record EncryptedPayload(byte[] salt, byte[] iv, byte[] ciphertext) { }

	private final SecureRandom random = new SecureRandom();

	public EncryptedPayload encrypt(byte[] plaintext, String passphrase) {
		try {
			byte[] salt = new byte[SALT_BYTES];
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(salt);
			random.nextBytes(iv);
			SecretKey key = deriveKey(passphrase, salt, ITERATIONS);
			Cipher cipher = Cipher.getInstance(CIPHER);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] ciphertext = cipher.doFinal(plaintext);
			return new EncryptedPayload(salt, iv, ciphertext);
		} catch (Exception ex) {
			throw new com.jobhub.common.error.BusinessRuleException("加密失败：" + ex.getMessage());
		}
	}

	public byte[] decrypt(EncryptedPayload payload, String passphrase) {
		try {
			SecretKey key = deriveKey(passphrase, payload.salt(), ITERATIONS);
			Cipher cipher = Cipher.getInstance(CIPHER);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload.iv()));
			return cipher.doFinal(payload.ciphertext());
		} catch (Exception ex) {
			throw new com.jobhub.common.error.BusinessRuleException("解密失败：" + ex.getMessage());
		}
	}

	private SecretKey deriveKey(String passphrase, byte[] salt, int iterations) throws Exception {
		PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_BITS);
		javax.crypto.SecretKeyFactory factory = javax.crypto.SecretKeyFactory.getInstance(KDF);
		byte[] keyBytes = factory.generateSecret(spec).getEncoded();
		return new SecretKeySpec(keyBytes, "AES");
	}
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd backend && mvn -q test -Dtest=EncryptionServiceTest 2>&1 | tail -5`
Expected: 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/jobhub/backup/application/EncryptionService.java backend/src/test/java/com/jobhub/backup/EncryptionServiceTest.java
git commit -m "feat(backup): add AES-256-GCM PBKDF2 encryption service"
```

---

### Task 3: BackupRecord domain + Mapper

**Files:**
- Create: `backend/src/main/java/com/jobhub/backup/domain/BackupRecord.java`
- Create: `backend/src/main/java/com/jobhub/backup/infrastructure/BackupRecordMapper.java`

**Interfaces:**
- Produces: `BackupRecord`（getter/setter 范式，仿 `DataExport`）；`BackupRecordMapper` `insert`/`selectList`/`selectById`。

- [ ] **Step 1: 写 domain**

```java
package com.jobhub.backup.domain;

/** 加密备份记录（追加型只读历史，无状态/版本）。salt+iv 落库供恢复派生密钥；passphrase/派生密钥永不持久化。 */
public class BackupRecord {
	private String id;
	private String createdAt;
	private String algorithm;
	private int pbkdf2Iterations;
	private byte[] salt;
	private byte[] iv;
	private String dataExportId;
	private String filePath;
	private String fileName;
	private long sizeBytes;

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getCreatedAt() { return createdAt; }
	public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
	public String getAlgorithm() { return algorithm; }
	public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
	public int getPbkdf2Iterations() { return pbkdf2Iterations; }
	public void setPbkdf2Iterations(int pbkdf2Iterations) { this.pbkdf2Iterations = pbkdf2Iterations; }
	public byte[] getSalt() { return salt; }
	public void setSalt(byte[] salt) { this.salt = salt; }
	public byte[] getIv() { return iv; }
	public void setIv(byte[] iv) { this.iv = iv; }
	public String getDataExportId() { return dataExportId; }
	public void setDataExportId(String dataExportId) { this.dataExportId = dataExportId; }
	public String getFilePath() { return filePath; }
	public void setFilePath(String filePath) { this.filePath = filePath; }
	public String getFileName() { return fileName; }
	public void setFileName(String fileName) { this.fileName = fileName; }
	public long getSizeBytes() { return sizeBytes; }
	public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
}
```

- [ ] **Step 2: 写 Mapper**

```java
package com.jobhub.backup.infrastructure;

import com.jobhub.backup.domain.BackupRecord;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface BackupRecordMapper {
	@Insert("""
		INSERT INTO backup_record (id, created_at, algorithm, pbkdf2_iterations, salt, iv,
			data_export_id, file_path, file_name, size_bytes)
		VALUES (#{id}, #{createdAt}, #{algorithm}, #{pbkdf2Iterations}, #{salt}, #{iv},
			#{dataExportId}, #{filePath}, #{fileName}, #{sizeBytes})
		""")
	int insert(BackupRecord record);

	@Select("SELECT id, created_at, algorithm, pbkdf2_iterations, salt, iv, data_export_id, "
		+ "file_path, file_name, size_bytes FROM backup_record WHERE id=#{id}")
	BackupRecord selectById(@Param("id") String id);

	@Select("SELECT id, created_at, algorithm, pbkdf2_iterations, salt, iv, data_export_id, "
		+ "file_path, file_name, size_bytes FROM backup_record ORDER BY created_at DESC")
	List<BackupRecord> selectList();
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/jobhub/backup/domain/BackupRecord.java backend/src/main/java/com/jobhub/backup/infrastructure/BackupRecordMapper.java
git commit -m "feat(backup): add BackupRecord domain and mapper"
```

---

### Task 4: BackupService

**Files:**
- Create: `backend/src/main/java/com/jobhub/backup/application/BackupService.java`
- Modify: `backend/src/main/resources/application.yml`（加 `jobhub.backup-dir`）

**Interfaces:**
- Consumes: `ExportService.create("JSON")`、`ExportService.readExportFile(DataExport)`、`EncryptionService.encrypt`、`BackupRecordMapper`、`IdGenerator`、`UtcTime`、`@Value("${jobhub.backup-dir:./data/backups}")`。

- [ ] **Step 1: 在 application.yml 加配置**

在 `jobhub:` 下 `export-dir` 之后加：
```yaml
  backup-dir: ${JOBHUB_BACKUP_DIR:./data/backups}
```

- [ ] **Step 2: 写 BackupService**

```java
package com.jobhub.backup.application;

import com.jobhub.backup.domain.BackupRecord;
import com.jobhub.backup.infrastructure.BackupRecordMapper;
import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.error.ResourceNotFoundException;
import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.datamanagement.application.ExportService;
import com.jobhub.datamanagement.domain.DataExport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 加密备份生成：复用 ExportService 标准数据包 → AES-256-GCM 加密 → 落盘 → 记录。
 * passphrase 仅传入不持久化；salt+iv 落库供未来恢复。
 */
@Service
public class BackupService {
	static final String ALGORITHM = "AES_256_GCM_PBKDF2";

	private final ExportService exportService;
	private final EncryptionService encryption;
	private final BackupRecordMapper mapper;
	private final IdGenerator ids;
	private final UtcTime time;
	private final String backupDir;

	public BackupService(ExportService exportService, EncryptionService encryption,
			BackupRecordMapper mapper, IdGenerator ids, UtcTime time,
			@Value("${jobhub.backup-dir:./data/backups}") String backupDir) {
		this.exportService = exportService;
		this.encryption = encryption;
		this.mapper = mapper;
		this.ids = ids;
		this.time = time;
		this.backupDir = backupDir;
	}

	@Transactional
	public BackupRecord create(String passphrase) {
		DataExport export = exportService.create("JSON");
		if (!"SUCCEEDED".equals(export.getStatus())) {
			throw new BusinessRuleException("数据包生成失败：" + export.getFailureReason());
		}
		byte[] plaintext = exportService.readExportFile(export);
		EncryptionService.EncryptedPayload payload = encryption.encrypt(plaintext, passphrase);

		String id = ids.newId();
		String fileName = id + ".enc";
		Path dir = Paths.get(backupDir);
		Path file = dir.resolve(fileName);
		try {
			Files.createDirectories(dir);
			Files.write(file, composeFileBytes(payload));
		} catch (Exception ex) {
			throw new BusinessRuleException("备份文件写入失败：" + ex.getMessage());
		}

		BackupRecord record = new BackupRecord();
		record.setId(id);
		record.setCreatedAt(time.now());
		record.setAlgorithm(ALGORITHM);
		record.setPbkdf2Iterations(EncryptionService.ITERATIONS);
		record.setSalt(payload.salt());
		record.setIv(payload.iv());
		record.setDataExportId(export.getId());
		record.setFilePath(file.toString());
		record.setFileName(fileName);
		record.setSizeBytes(file.toFile().length());
		mapper.insert(record);
		return record;
	}

	public List<BackupRecord> list() {
		return mapper.selectList();
	}

	public BackupRecord get(String id) {
		BackupRecord record = mapper.selectById(id);
		if (record == null) {
			throw new ResourceNotFoundException("BackupRecord", id);
		}
		return record;
	}

	public byte[] readFileBytes(BackupRecord record) {
		Path file = Paths.get(record.getFilePath());
		if (!Files.exists(file)) {
			throw new ResourceNotFoundException("BackupFile", record.getId());
		}
		try {
			return Files.readAllBytes(file);
		} catch (Exception ex) {
			throw new ResourceNotFoundException("BackupFile", record.getId());
		}
	}

	private byte[] composeFileBytes(EncryptionService.EncryptedPayload payload) {
		byte[] salt = payload.salt();
		byte[] iv = payload.iv();
		byte[] ct = payload.ciphertext();
		byte[] out = new byte[salt.length + iv.length + ct.length];
		System.arraycopy(salt, 0, out, 0, salt.length);
		System.arraycopy(iv, 0, out, salt.length, iv.length);
		System.arraycopy(ct, 0, out, salt.length + iv.length, ct.length);
		return out;
	}
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/jobhub/backup/application/BackupService.java backend/src/main/resources/application.yml
git commit -m "feat(backup): add BackupService reusing export payload"
```

---

### Task 5: API 层（Controller + Request/Response）+ OpenAPI

**Files:**
- Create: `backend/src/main/java/com/jobhub/backup/api/BackupCreateRequest.java`
- Create: `backend/src/main/java/com/jobhub/backup/api/BackupRecordResponse.java`
- Create: `backend/src/main/java/com/jobhub/backup/api/BackupController.java`
- Modify: `docs/jobhub/03-openapi.yaml`

**Interfaces:**
- Produces: `BackupCreateRequest` record（`@NotBlank @Size(min=8,max=256) passphrase`）；`BackupRecordResponse.from(BackupRecord)`（不输出 salt/iv）。

- [ ] **Step 1: 写 Request**

```java
package com.jobhub.backup.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BackupCreateRequest(
		@NotBlank @Size(min = 8, max = 256) String passphrase) { }
```

- [ ] **Step 2: 写 Response**

```java
package com.jobhub.backup.api;

import com.jobhub.backup.domain.BackupRecord;

public record BackupRecordResponse(String id, String createdAt, String algorithm,
		int pbkdf2Iterations, String dataExportId, String fileName, long sizeBytes) {
	public static BackupRecordResponse from(BackupRecord r) {
		return new BackupRecordResponse(r.getId(), r.getCreatedAt(), r.getAlgorithm(),
			r.getPbkdf2Iterations(), r.getDataExportId(), r.getFileName(), r.getSizeBytes());
	}
}
```

- [ ] **Step 3: 写 Controller**

```java
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
```

- [ ] **Step 4: 在 03-openapi.yaml 补 3 端点 + schema + BackupId 参数**

在 `/data-exports/{exportId}/download` 路径块之后（或在 paths 末尾合适位置）新增 `/backups` 与 `/backups/{backupId}/download`（内容见 spec §1）；在 `components/parameters` 加 `BackupId`；在 `components/schemas` 加 `BackupCreateRequest` 与 `BackupRecord`（不暴露 salt/iv 字段）。

- [ ] **Step 5: 重新生成 types 并 typecheck**

Run: `cd frontend && npm run gen-types && npm run typecheck 2>&1 | tail -5`
Expected: 通过，`types.ts` 含 `/backups` 端点。

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/jobhub/backup/api/ docs/jobhub/03-openapi.yaml
git commit -m "feat(backup): add backup REST API and OpenAPI contract"
```

---

### Task 6: 集成测试（AT-36）

**Files:**
- Create: `backend/src/test/java/com/jobhub/integration/BackupIntegrationTest.java`
- Modify: `docs/jobhub/05-acceptance-test-cases.md`（加 AT-36）、`docs/jobhub/02-state-machines.md`（加 §9）、`jobhub-prd.md`（§10/V1.0 标注）

- [ ] **Step 1: 写失败集成测试**

参考既有 `ExportIntegrationTest` / `NotificationChannelIntegrationTest` 的 `@SpringBootTest(webEnvironment=RANDOM_PORT)` + `restTemplate` + `TestFixtures` 范式。测试 AT-36：POST passphrase → 201 + algorithm/iterations/dataExportId/fileName/sizeBytes + 响应不含 `passphrase`；GET list 含该记录；GET download 字节数=sizeBytes；短 passphrase → 400。用 `@Sql` 或建一个 job 保证有可导出数据；用临时 `JOBHUB_BACKUP_DIR` 指向 `target` 下临时目录避免污染。

- [ ] **Step 2: 运行确认通过**

Run: `cd backend && mvn -q test -Dtest=BackupIntegrationTest 2>&1 | tail -10`
Expected: tests passed，Flyway V1→V24 成功。

- [ ] **Step 3: 更新规格文档（AT-36、状态机 §9、PRD 标注）**

按 spec §3、§5、§11 更新 `05-acceptance-test-cases.md`（加 AT-36）、`02-state-machines.md`（加 §9 备份记录说明）、`jobhub-prd.md`（§10 P2 / §19 V1.0 标注最小切片已实现，恢复与定时留后续）。

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/jobhub/integration/BackupIntegrationTest.java docs/jobhub/05-acceptance-test-cases.md docs/jobhub/02-state-machines.md jobhub-prd.md
git commit -m "test(backup): AT-36 integration test and spec updates"
```

---

### Task 7: 前端 UI

**Files:**
- Create: `frontend/src/api/backup/backupApi.ts`
- Create: `frontend/src/features/settings/EncryptedBackupSection.tsx`
- Modify: `frontend/src/features/settings/SettingsPage.tsx`（插入区块）
- Regenerate: `frontend/src/api/generated/types.ts`（不入库）

- [ ] **Step 1: 写 backupApi.ts**

`createBackup(passphrase)` POST /backups；`listBackups()` GET /backups；`downloadBackup(id)` 用 anchor 下载 `GET /backups/{id}/download`（带 credentials）；含 `useBackups` query 与 `useCreateBackup` mutation（TanStack Query 范式，仿 `useChannelQueries`）。

- [ ] **Step 2: 写 EncryptedBackupSection.tsx**

受控 passphrase 输入（type=password）+ 「立即加密备份」按钮 + 列表（时间/文件名/大小/下载）+ 提交后清空 passphrase + 不显示 passphrase、不显示恢复。

- [ ] **Step 3: 在 SettingsPage.tsx 插入 `<EncryptedBackupSection />`**

- [ ] **Step 4: 重新生成 types + typecheck + lint + build**

Run: `cd frontend && npm run gen-types && npm run typecheck && npm run lint && npm run build 2>&1 | tail -10`
Expected: 全部通过。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/backup/backupApi.ts frontend/src/features/settings/EncryptedBackupSection.tsx frontend/src/features/settings/SettingsPage.tsx
git commit -m "feat(backup): add encrypted backup settings UI"
```

---

### Task 8: 全量回归与 E2E

- [ ] **Step 1: 后端全量测试**

Run: `cd backend && mvn -q clean test 2>&1 | tail -5`
Expected: 全部通过（含新 BackupIntegrationTest + 既有 102），Flyway V1→V24。

- [ ] **Step 2: 全量 E2E**

Run: `cd frontend && npx playwright test --reporter=dot 2>&1 | tail -15`
Expected: 既有用例不回归（首跑若有既有 flaky 用例偶发失败，单独复跑通过即可，与本切片无关）。

- [ ] **Step 3: git diff --check**

Run: `git diff --check`
Expected: 通过。

- [ ] **Step 4: 更新 IMPLEMENTATION_STATUS.md**

新增窗口 `2026-09-06-02`：目标/已完成/未完成/修改文件/验证命令与结果/下一窗口只做/不要重复做。记录单窗口边界偏差（约 19 文件）。

- [ ] **Step 5: Commit + 合并 + 推送**

```bash
git add docs/jobhub/IMPLEMENTATION_STATUS.md
git commit -m "docs: record encrypted backup window"
git checkout main && git merge --ff-only dev
git push origin dev && git push origin main
```

复核 `origin/main` = `origin/dev` = `main` = `dev` 指向同一 commit。
