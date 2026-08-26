package com.jobhub.integration.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * JSON 探针：用 Jackson 解析 TestRestTemplate 响应体，简化字段断言（替代 MockMvc 的 jsonPath DSL）。
 * 不抛异常时按路径取值；取不到返回 null。
 */
public final class JsonProbe {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private JsonProbe() { }

	/** 读取根字段为字符串。支持点号嵌套如 "requirement.confirmationStatus"。 */
	public static String str(String json, String path) {
		JsonNode node = node(json, path);
		return node == null ? null : (node.isTextual() ? node.asText() : node.toString());
	}

	/** 读取根字段为 Long。 */
	public static Long lng(String json, String path) {
		JsonNode node = node(json, path);
		return node == null ? null : node.asLong();
	}

	/** 读取根字段为整型。 */
	public static Integer intVal(String json, String path) {
		JsonNode node = node(json, path);
		return node == null ? null : node.asInt();
	}

	/** 读取数组长度。 */
	public static int arraySize(String json, String path) {
		JsonNode node = node(json, path);
		return node == null ? 0 : node.size();
	}

	/** 取数组某下标的字段值（字符串，支持点号嵌套如 "requirement.confirmationStatus"）。如 "candidates.0.confirmationStatus"。 */
	public static String arrStr(String json, String arrayPath, int index, String field) {
		JsonNode el = arrayElement(json, arrayPath, index);
		if (el == null) return null;
		JsonNode fieldNode = drill(el, field);
		return fieldNode == null ? null : (fieldNode.isTextual() ? fieldNode.asText() : fieldNode.toString());
	}

	/** 取数组元素整型字段。 */
	public static Long arrLng(String json, String arrayPath, int index, String field) {
		JsonNode el = arrayElement(json, arrayPath, index);
		if (el == null) return null;
		JsonNode fieldNode = drill(el, field);
		return fieldNode == null ? null : fieldNode.asLong();
	}

	/** 取数组元素某下标节点。 */
	private static JsonNode arrayElement(String json, String arrayPath, int index) {
		JsonNode arr = node(json, arrayPath);
		if (arr == null || !arr.isArray() || index >= arr.size()) {
			return null;
		}
		return arr.get(index);
	}

	/** 从节点按点号路径逐层取值。 */
	private static JsonNode drill(JsonNode start, String path) {
		JsonNode cur = start;
		for (String seg : path.split("\\.")) {
			if (cur == null) return null;
			cur = cur.get(seg);
		}
		return cur;
	}

	private static JsonNode node(String json, String path) {
		try {
			JsonNode root = MAPPER.readTree(json);
			if (path == null || path.isEmpty()) {
				return root;
			}
			JsonNode cur = root;
			for (String seg : path.split("\\.")) {
				if (cur == null) return null;
				cur = cur.get(seg);
			}
			return cur;
		} catch (Exception ex) {
			throw new IllegalStateException("Cannot parse JSON for path " + path + ": " + ex.getMessage(), ex);
		}
	}

	/** 遍历数组字段，收集所有值（用于断言全部候选项 confirmationStatus=PENDING）。arrayPath 为空表示根数组。 */
	public static List<String> collectArrayField(String json, String arrayPath, String field) {
		JsonNode arr = node(json, arrayPath);
		if (arr == null || !arr.isArray()) return List.of();
		return java.util.stream.StreamSupport.stream(arr.spliterator(), false)
				.map(el -> {
					JsonNode f = el.get(field);
					return f == null ? null : (f.isTextual() ? f.asText() : f.toString());
				})
				.toList();
	}
}
