package com.jobhub.datamanagement.application;

import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.error.ResourceNotFoundException;
import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.common.version.VersionCheck;
import com.jobhub.datamanagement.domain.DataExport;
import com.jobhub.datamanagement.infrastructure.DataExportMapper;
import com.jobhub.datamanagement.infrastructure.ExportDataMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 数据导出。P0 在创建请求内同步完成：写入业务数据 JSON 文件并更新任务状态。
 * 导出内容为业务数据及关联 ID；排除机密与运行记录由 ExportDataMapper 的表选择保证。
 */
@Service
public class ExportService {
	private static final ObjectMapper JSON = new ObjectMapper();

	private final DataExportMapper dataExportMapper;
	private final ExportDataMapper exportDataMapper;
	private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
	private final IdGenerator ids;
	private final UtcTime time;
	private final String exportDir;

	public ExportService(DataExportMapper dataExportMapper, ExportDataMapper exportDataMapper,
			org.springframework.jdbc.core.JdbcTemplate jdbcTemplate, IdGenerator ids, UtcTime time,
			@Value("${jobhub.export-dir:./data/exports}") String exportDir) {
		this.dataExportMapper = dataExportMapper;
		this.exportDataMapper = exportDataMapper;
		this.jdbcTemplate = jdbcTemplate;
		this.ids = ids;
		this.time = time;
		this.exportDir = exportDir;
	}

	public DataExport get(String id) {
		DataExport export = dataExportMapper.selectById(id);
		VersionCheck.requireFound(export, "DataExport", id);
		return export;
	}

	public DataExport create(String format) {
		if (!"JSON".equals(format) && !"CSV".equals(format)) {
			throw new BusinessRuleException("仅支持 JSON 或 CSV 导出");
		}
		String now = time.now();
		DataExport export = DataExport.create(ids.newId(), format, now);
		dataExportMapper.insert(export);
		dataExportMapper.updateStatus(export.getId(), "RUNNING", time.now());
		try {
			Path file = "CSV".equals(format)
				? writeCsvExportFile(export.getId())
				: writeExportFile(export.getId());
			dataExportMapper.complete(export.getId(), file.toString(), time.now());
		} catch (Exception ex) {
			dataExportMapper.fail(export.getId(), "导出失败：" + ex.getMessage(), time.now());
		}
		return get(export.getId());
	}

	public byte[] readExportFile(DataExport export) {
		if (!"SUCCEEDED".equals(export.getStatus()) || export.getDownloadPath() == null) {
			throw new ResourceNotFoundException("ExportFile", export.getId());
		}
		Path file = Paths.get(export.getDownloadPath());
		if (!Files.exists(file)) {
			throw new ResourceNotFoundException("ExportFile", export.getId());
		}
		try {
			return Files.readAllBytes(file);
		} catch (Exception ex) {
			throw new ResourceNotFoundException("ExportFile", export.getId());
		}
	}

	private Path writeExportFile(String exportId) throws Exception {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("format", "JSON");
		payload.put("exportedAt", time.now());
		payload.put("tables", collectTables());
		String json = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
		Path dir = Paths.get(exportDir);
		Files.createDirectories(dir);
		Path file = dir.resolve("jobhub-export-" + exportId + ".json");
		Files.writeString(file, json, StandardCharsets.UTF_8);
		return file;
	}

	/**
	 * CSV 导出：全部业务表逐表写入 {table}.csv（UTF-8 BOM 便于 Excel 打开，RFC 4180 转义），
	 * 打包为 ZIP。列名取自数据库实际 schema（排除 idempotency_key），空表仅写表头。
	 */
	private Path writeCsvExportFile(String exportId) throws Exception {
		Map<String, List<Map<String, Object>>> tables = collectTables();
		Path dir = Paths.get(exportDir);
		Files.createDirectories(dir);
		Path file = dir.resolve("jobhub-export-" + exportId + ".zip");
		try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(
				new java.io.BufferedOutputStream(Files.newOutputStream(file), 64 * 1024))) {
			for (Map.Entry<String, List<Map<String, Object>>> entry : tables.entrySet()) {
				zip.putNextEntry(new java.util.zip.ZipEntry(entry.getKey() + ".csv"));
				writeCsv(zip, entry.getKey(), entry.getValue());
				zip.closeEntry();
			}
		}
		return file;
	}

	private static final byte[] CRLF = {'\r', '\n'};

	private void writeCsv(java.io.OutputStream out, String tableName, List<Map<String, Object>> rows)
			throws Exception {
		// UTF-8 BOM：Excel 依赖 BOM 识别编码
		out.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
		List<String> columns = csvColumns(tableName);
		out.write(escapeCsvRow(columns).getBytes(StandardCharsets.UTF_8));
		out.write(CRLF);
		for (Map<String, Object> row : rows) {
			List<String> values = new java.util.ArrayList<>(columns.size());
			for (String column : columns) {
				Object value = row.get(column);
				values.add(value == null ? "" : String.valueOf(value));
			}
			out.write(escapeCsvRow(values).getBytes(StandardCharsets.UTF_8));
			out.write(CRLF);
		}
	}

	private List<String> csvColumns(String tableName) {
		List<String> columns = new java.util.ArrayList<>();
		for (Map<String, Object> column : jdbcTemplate.queryForList("PRAGMA table_info('" + tableName + "')")) {
			String name = String.valueOf(column.get("name"));
			if ("idempotency_key".equals(name)) {
				continue;
			}
			columns.add(name);
		}
		return columns;
	}

	private String escapeCsvRow(List<String> values) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				builder.append(',');
			}
			builder.append(escapeCsvField(values.get(i)));
		}
		return builder.toString();
	}

	private String escapeCsvField(String value) {
		if (value == null) {
			return "";
		}
		if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\r') >= 0
				|| value.indexOf('\n') >= 0) {
			return '"' + value.replace("\"", "\"\"") + '"';
		}
		return value;
	}

	private Map<String, List<Map<String, Object>>> collectTables() {
		Map<String, List<Map<String, Object>>> tables = new LinkedHashMap<>();
		tables.put("job_posting", exportDataMapper.selectJobPostings());
		tables.put("job_requirement", exportDataMapper.selectJobRequirements());
		tables.put("requirement_match", exportDataMapper.selectRequirementMatches());
		tables.put("requirement_skill", exportDataMapper.selectRequirementSkills());
		tables.put("application_record", exportDataMapper.selectApplicationRecords());
		tables.put("application_status_log", exportDataMapper.selectApplicationStatusLogs());
		tables.put("interview_schedule", exportDataMapper.selectInterviewSchedules());
		tables.put("interview_checklist_item", exportDataMapper.selectInterviewChecklistItems());
		tables.put("interview_reminder", exportDataMapper.selectInterviewReminders());
		tables.put("interview_review", exportDataMapper.selectInterviewReviews());
		tables.put("interview_question", exportDataMapper.selectInterviewQuestions());
		tables.put("question_knowledge", exportDataMapper.selectQuestionKnowledge());
		tables.put("knowledge_point", exportDataMapper.selectKnowledgePoints());
		tables.put("learning_task", exportDataMapper.selectLearningTasks());
		tables.put("task_source", exportDataMapper.selectTaskSources());
		tables.put("skill", exportDataMapper.selectSkills());
		tables.put("skill_alias", exportDataMapper.selectSkillAliases());
		tables.put("user_skill", exportDataMapper.selectUserSkills());
		tables.put("skill_evidence", exportDataMapper.selectSkillEvidence());
		tables.put("project", exportDataMapper.selectProjects());
		tables.put("evidence", exportDataMapper.selectEvidence());
		tables.put("project_evidence", exportDataMapper.selectProjectEvidence());
		tables.put("notification", exportDataMapper.selectNotifications());
		return tables;
	}
}
