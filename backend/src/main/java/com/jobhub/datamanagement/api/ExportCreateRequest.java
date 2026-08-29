package com.jobhub.datamanagement.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ExportCreateRequest(
	@NotBlank @Pattern(regexp = "JSON", message = "仅支持 JSON 导出") String format
) { }
