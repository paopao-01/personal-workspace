package com.jobhub.job.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 合并同一岗位的重复候选要求：来源要求软删除并指向目标，目标要求原样保留。
 */
public record RequirementMergeRequest(
	@NotBlank String targetRequirementId,
	@NotEmpty List<@NotBlank String> sourceRequirementIds
) { }
