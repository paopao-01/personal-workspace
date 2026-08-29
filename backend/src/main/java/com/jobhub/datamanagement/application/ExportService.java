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
	private final IdGenerator ids;
	private final UtcTime time;
	private final String exportDir;

	public ExportService(DataExportMapper dataExportMapper, ExportDataMapper exportDataMapper,
			IdGenerator ids, UtcTime time,
			@Value("${jobhub.export-dir:./data/exports}") String exportDir) {
		this.dataExportMapper = dataExportMapper;
		this.exportDataMapper = exportDataMapper;
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
		if (!"JSON".equals(format)) {
			throw new BusinessRuleException("Only JSON export is supported");
		}
		String now = time.now();
		DataExport export = DataExport.create(ids.newId(), now);
		dataExportMapper.insert(export);
		dataExportMapper.updateStatus(export.getId(), "RUNNING", time.now());
		try {
			Path file = writeExportFile(export.getId());
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

	private Map<String, Object> collectTables() {
		Map<String, Object> tables = new LinkedHashMap<>();
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
