package com.jobhub.datamanagement.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.datamanagement.api.ImportIssueResponse;
import com.jobhub.datamanagement.api.ImportResultResponse;
import com.jobhub.datamanagement.api.ImportValidationResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据导入与完整恢复（PRD 9.5）。数据包为导出端点生成的标准 JSON（{format, exportedAt, tables}）。
 *
 * 冲突预检：同键已存在且内容一致 → duplicateIdentical；同键已存在但内容不同 → conflict（用户事实优先，
 * 恢复时保留现状）；外键父行在数据库与数据包中均不存在 → missingParent。
 * 恢复语义：只插入缺失行，不修改、不覆盖任何已有行，因此重复恢复同一数据包天然幂等。
 * 排除表（用户资料/设置、审计、幂等、导出任务、回收站）按未知表跳过；列以 pragma_table_info 白名单为准。
 */
@Service
public class ImportService {

	/** 已知可导入表，按外键安全顺序排列（父表先于子表）。 */
	private static final List<String> IMPORT_ORDER = List.of(
		"job_posting", "skill", "knowledge_point", "evidence", "project", "user_skill", "skill_alias",
		"job_requirement", "requirement_skill", "requirement_match", "application_record",
		"application_status_log", "interview_schedule", "interview_checklist_item", "interview_reminder",
		"interview_review", "interview_question", "question_knowledge", "learning_task", "task_source",
		"project_evidence", "skill_evidence", "notification");

	/** 复合主键表（无 id 列）；其余表主键为 id。 */
	private static final Map<String, List<String>> KEY_COLUMNS = Map.of(
		"question_knowledge", List.of("question_id", "knowledge_point_id"),
		"skill_evidence", List.of("skill_id", "evidence_id"),
		"project_evidence", List.of("project_id", "evidence_id"),
		"requirement_skill", List.of("requirement_id", "skill_id"));

	private record FkRef(String column, String parentTable, boolean nullable) { }

	/** 外键引用图；user_skill.user_id 指向不导出的 user_profile（本机单例），豁免预检。 */
	private static final Map<String, List<FkRef>> FK_REFS = Map.ofEntries(
		Map.entry("job_requirement", List.of(new FkRef("job_id", "job_posting", false),
			new FkRef("merged_into_requirement_id", "job_requirement", true))),
		Map.entry("requirement_match", List.of(new FkRef("requirement_id", "job_requirement", false))),
		Map.entry("requirement_skill", List.of(new FkRef("requirement_id", "job_requirement", false),
			new FkRef("skill_id", "skill", false))),
		Map.entry("application_record", List.of(new FkRef("job_id", "job_posting", false))),
		Map.entry("application_status_log", List.of(new FkRef("application_id", "application_record", false))),
		Map.entry("interview_schedule", List.of(new FkRef("application_id", "application_record", false))),
		Map.entry("interview_checklist_item", List.of(new FkRef("interview_id", "interview_schedule", false))),
		Map.entry("interview_reminder", List.of(new FkRef("interview_id", "interview_schedule", false))),
		Map.entry("interview_review", List.of(new FkRef("interview_id", "interview_schedule", false))),
		Map.entry("interview_question", List.of(new FkRef("review_id", "interview_review", false))),
		Map.entry("question_knowledge", List.of(new FkRef("question_id", "interview_question", false),
			new FkRef("knowledge_point_id", "knowledge_point", false))),
		Map.entry("task_source", List.of(new FkRef("task_id", "learning_task", false))),
		Map.entry("skill_alias", List.of(new FkRef("skill_id", "skill", false))),
		Map.entry("user_skill", List.of(new FkRef("skill_id", "skill", false))),
		Map.entry("skill_evidence", List.of(new FkRef("skill_id", "skill", false),
			new FkRef("evidence_id", "evidence", false))),
		Map.entry("project_evidence", List.of(new FkRef("project_id", "project", false),
			new FkRef("evidence_id", "evidence", false))),
		Map.entry("notification", List.of(new FkRef("reminder_id", "interview_reminder", true))),
		Map.entry("knowledge_point", List.of(new FkRef("merged_into_knowledge_point_id", "knowledge_point", true))));

	private record TablePlan(
		String tableName,
		int packageRows,
		List<Map<String, Object>> toInsert,
		int duplicateIdentical,
		int conflict,
		int missingParent,
		List<ImportIssueResponse> issues
	) { }

	private record PackageData(String exportedAt, List<TablePlan> plans, int totalRows) { }

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public ImportService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public ImportValidationResponse validate(JsonNode body) {
		PackageData data = computePlans(body);
		int insertableRows = data.plans().stream().mapToInt(plan -> plan.toInsert().size()).sum();
		List<ImportValidationResponse.TablePreview> previews = data.plans().stream()
			.map(plan -> new ImportValidationResponse.TablePreview(plan.tableName(), plan.packageRows(),
				plan.toInsert().size(), plan.duplicateIdentical(), plan.conflict(), plan.missingParent()))
			.toList();
		List<ImportIssueResponse> issues = data.plans().stream().flatMap(plan -> plan.issues().stream()).toList();
		boolean valid = issues.stream().noneMatch(issue -> ImportIssueResponse.TYPE_INVALID_PACKAGE.equals(issue.type()));
		return new ImportValidationResponse(valid, data.exportedAt(), data.totalRows(), insertableRows, previews, issues);
	}

	@Transactional
	public ImportResultResponse restore(JsonNode body) {
		PackageData data = computePlans(body);
		Map<String, List<String>> columnWhitelist = loadColumnWhitelist();
		List<ImportIssueResponse> issues = new ArrayList<>();
		List<ImportResultResponse.TableResult> results = new ArrayList<>();
		int inserted = 0;
		int skippedIdentical = 0;
		int skippedConflict = 0;
		int skippedMissingParent = 0;
		int failed = 0;
		for (TablePlan plan : data.plans()) {
			issues.addAll(plan.issues());
			skippedIdentical += plan.duplicateIdentical();
			skippedConflict += plan.conflict();
			skippedMissingParent += plan.missingParent();
			int tableInserted = 0;
			int tableFailed = 0;
			for (Map<String, Object> row : plan.toInsert()) {
				try {
					insertRow(plan.tableName(), row, columnWhitelist.get(plan.tableName()));
					tableInserted++;
				} catch (Exception ex) {
					tableFailed++;
					issues.add(new ImportIssueResponse(ImportIssueResponse.TYPE_ROW_FAILED, plan.tableName(),
						rowKeyValue(plan.tableName(), row), "插入失败：" + ex.getMessage()));
				}
			}
			inserted += tableInserted;
			failed += tableFailed;
			results.add(new ImportResultResponse.TableResult(plan.tableName(), tableInserted,
				plan.duplicateIdentical(), plan.conflict(), plan.missingParent(), tableFailed));
		}
		return new ImportResultResponse(inserted, skippedIdentical, skippedConflict, skippedMissingParent, failed,
			results, issues);
	}

	private PackageData computePlans(JsonNode body) {
		if (body == null || !body.isObject()) {
			throw new BusinessRuleException("数据包必须是 JSON 对象");
		}
		JsonNode format = body.get("format");
		if (format == null || !format.isTextual() || !"JSON".equals(format.asText())) {
			throw new BusinessRuleException("数据包 format 必须为 JSON");
		}
		JsonNode tablesNode = body.get("tables");
		if (tablesNode == null || !tablesNode.isObject()) {
			throw new BusinessRuleException("数据包缺少 tables 对象");
		}
		String exportedAt = body.path("exportedAt").isTextual() ? body.get("exportedAt").asText() : null;

		Map<String, Map<String, Map<String, Object>>> dbRows = loadDbRows();
		Map<String, Set<String>> availableIds = buildAvailableIds(dbRows, tablesNode);

		List<TablePlan> plans = new ArrayList<>();
		int totalRows = 0;
		List<TablePlan> unknownTablePlans = new ArrayList<>();
		for (var field : tablesNode.properties()) {
			String tableName = field.getKey();
			if (!IMPORT_ORDER.contains(tableName)) {
				JsonNode rowsNode = field.getValue();
				int rows = rowsNode.isArray() ? rowsNode.size() : 0;
				unknownTablePlans.add(new TablePlan(tableName, rows, List.of(), 0, 0, 0,
					List.of(new ImportIssueResponse(ImportIssueResponse.TYPE_UNKNOWN_TABLE, tableName, null,
						"表不在可导入范围内（排除表或未知表），已跳过 " + rows + " 行"))));
			}
		}
		for (String tableName : IMPORT_ORDER) {
			JsonNode rowsNode = tablesNode.get(tableName);
			if (rowsNode == null) {
				continue;
			}
			plans.add(buildPlan(tableName, rowsNode, dbRows.get(tableName), availableIds));
		}
		plans.addAll(unknownTablePlans);
		totalRows = plans.stream().mapToInt(TablePlan::packageRows).sum();
		return new PackageData(exportedAt, plans, totalRows);
	}

	private TablePlan buildPlan(String tableName, JsonNode rowsNode, Map<String, Map<String, Object>> dbRows,
			Map<String, Set<String>> availableIds) {
		List<String> keyColumns = keyColumns(tableName);
		List<ImportIssueResponse> issues = new ArrayList<>();
		List<Map<String, Object>> toInsert = new ArrayList<>();
		int packageRows = 0;
		int duplicateIdentical = 0;
		int conflict = 0;
		int missingParent = 0;
		Set<String> seenKeys = new LinkedHashSet<>();
		if (!rowsNode.isArray()) {
			issues.add(new ImportIssueResponse(ImportIssueResponse.TYPE_INVALID_PACKAGE, tableName, null,
				"表数据必须是行数组"));
			return new TablePlan(tableName, 0, List.of(), 0, 0, 0, issues);
		}
		for (JsonNode rowNode : rowsNode) {
			if (!rowNode.isObject()) {
				issues.add(new ImportIssueResponse(ImportIssueResponse.TYPE_INVALID_PACKAGE, tableName, null,
					"行必须是对象"));
				continue;
			}
			packageRows++;
			Map<String, Object> row = objectMapper.convertValue(rowNode, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
			String keyValue = rowKey(tableName, keyColumns, row);
			if (keyValue == null) {
				issues.add(new ImportIssueResponse(ImportIssueResponse.TYPE_INVALID_PACKAGE, tableName, null,
					"行缺少主键列 " + keyColumns));
				continue;
			}
			if (!seenKeys.add(keyValue)) {
				conflict++;
				issues.add(new ImportIssueResponse(ImportIssueResponse.TYPE_CONFLICT, tableName, keyValue,
					"数据包内存在重复主键，仅保留首行，其余跳过"));
				continue;
			}
			Map<String, Object> existing = dbRows.get(keyValue);
			if (existing != null) {
				if (rowsEqual(tableName, row, existing)) {
					duplicateIdentical++;
				} else {
					conflict++;
					issues.add(new ImportIssueResponse(ImportIssueResponse.TYPE_CONFLICT, tableName, keyValue,
						"同键行已存在且内容不同，恢复时保留数据库现状"));
				}
				continue;
			}
			String missingFk = findMissingParent(tableName, row, availableIds);
			if (missingFk != null) {
				missingParent++;
				issues.add(new ImportIssueResponse(ImportIssueResponse.TYPE_MISSING_PARENT, tableName, keyValue,
					"外键父行缺失：" + missingFk));
				continue;
			}
			toInsert.add(row);
		}
		return new TablePlan(tableName, packageRows, toInsert, duplicateIdentical, conflict, missingParent, issues);
	}

	private String findMissingParent(String tableName, Map<String, Object> row, Map<String, Set<String>> availableIds) {
		List<FkRef> refs = FK_REFS.get(tableName);
		if (refs == null) {
			return null;
		}
		for (FkRef ref : refs) {
			Object value = row.get(ref.column());
			if (value == null || (value instanceof String text && text.isBlank())) {
				if (ref.nullable()) {
					continue;
				}
				return ref.column() + "（必填外键为空，父表 " + ref.parentTable() + "）";
			}
			if (!availableIds.getOrDefault(ref.parentTable(), Set.of()).contains(String.valueOf(value))) {
				return ref.column() + " → " + ref.parentTable() + "(" + value + ")";
			}
		}
		return null;
	}

	private Map<String, Map<String, Map<String, Object>>> loadDbRows() {
		Map<String, Map<String, Map<String, Object>>> dbRows = new LinkedHashMap<>();
		for (String tableName : IMPORT_ORDER) {
			List<String> keyColumns = keyColumns(tableName);
			Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
			List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM \"" + tableName + "\"");
			for (Map<String, Object> row : rows) {
				byKey.put(rowKey(tableName, keyColumns, row), row);
			}
			dbRows.put(tableName, byKey);
		}
		return dbRows;
	}

	private Map<String, Set<String>> buildAvailableIds(Map<String, Map<String, Map<String, Object>>> dbRows,
			JsonNode tablesNode) {
		Map<String, Set<String>> availableIds = new HashMap<>();
		for (String tableName : IMPORT_ORDER) {
			Set<String> ids = new LinkedHashSet<>(dbRows.get(tableName).keySet());
			JsonNode rowsNode = tablesNode.get(tableName);
			if (rowsNode != null && rowsNode.isArray()) {
				List<String> keyColumns = keyColumns(tableName);
				for (JsonNode rowNode : rowsNode) {
					if (rowNode.isObject()) {
						Map<String, Object> row = objectMapper.convertValue(rowNode,
							new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
						String key = rowKey(tableName, keyColumns, row);
						if (key != null) {
							ids.add(key);
						}
					}
				}
			}
			availableIds.put(tableName, ids);
		}
		return availableIds;
	}

	private String rowKey(String tableName, List<String> keyColumns, Map<String, Object> row) {
		List<String> parts = new ArrayList<>();
		for (String column : keyColumns) {
			Object value = row.get(column);
			if (value == null || String.valueOf(value).isBlank()) {
				return null;
			}
			parts.add(String.valueOf(value));
		}
		return String.join("|", parts);
	}

	private Map<String, List<String>> loadColumnWhitelist() {
		Map<String, List<String>> whitelist = new HashMap<>();
		for (String tableName : IMPORT_ORDER) {
			List<Map<String, Object>> columns = jdbcTemplate
				.queryForList("PRAGMA table_info('" + tableName + "')");
			whitelist.put(tableName, columns.stream().map(column -> String.valueOf(column.get("name"))).toList());
		}
		return whitelist;
	}

	private void insertRow(String tableName, Map<String, Object> row, List<String> allowedColumns) {
		List<String> columns = new ArrayList<>();
		List<Object> values = new ArrayList<>();
		for (String column : allowedColumns) {
			if (row.containsKey(column)) {
				columns.add(column);
				values.add(row.get(column));
			}
		}
		if (columns.isEmpty()) {
			return;
		}
		StringBuilder sql = new StringBuilder("INSERT INTO \"").append(tableName).append("\" (");
		StringBuilder placeholders = new StringBuilder();
		for (int i = 0; i < columns.size(); i++) {
			if (i > 0) {
				sql.append(", ");
				placeholders.append(", ");
			}
			sql.append('"').append(columns.get(i)).append('"');
			placeholders.append('?');
		}
		sql.append(") VALUES (").append(placeholders).append(')');
		jdbcTemplate.update(sql.toString(), values.toArray());
	}

	private String rowKeyValue(String tableName, Map<String, Object> row) {
		return rowKey(tableName, keyColumns(tableName), row);
	}

	private List<String> keyColumns(String tableName) {
		return KEY_COLUMNS.getOrDefault(tableName, List.of("id"));
	}

	/**
	 * 比较导出行与数据库行（仅按导出行包含的列比较）。SQLite TEXT 亲和列会把数字存为文本，
	 * 因此数字与数字字符串视为相等；空串与 null 视为相等（导入/导出往返中空白语义一致）。
	 */
	private boolean rowsEqual(String tableName, Map<String, Object> exported, Map<String, Object> existing) {
		for (Map.Entry<String, Object> entry : exported.entrySet()) {
			String column = entry.getKey();
			Object exportedValue = entry.getValue();
			if (!existing.containsKey(column)) {
				continue;
			}
			Object existingValue = existing.get(column);
			if (!valuesEqual(exportedValue, existingValue)) {
				return false;
			}
		}
		return true;
	}

	private boolean valuesEqual(Object exportedValue, Object existingValue) {
		if (exportedValue == null && existingValue == null) {
			return true;
		}
		if (exportedValue instanceof Number exportedNumber && existingValue instanceof Number existingNumber) {
			return exportedNumber.doubleValue() == existingNumber.doubleValue();
		}
		String exportedText = exportedValue == null ? null : String.valueOf(exportedValue);
		String existingText = existingValue == null ? null : String.valueOf(existingValue);
		return exportedText == null ? existingText == null : exportedText.equals(existingText)
			|| (exportedText.isBlank() && (existingText == null || existingText.isBlank()));
	}
}
