package com.jobhub.skill.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 显式修改技能自评等级。reason 为用户填写理由，当前 schema 未提供存储列，接受但不持久化。
 */
public record SelfLevelUpdateRequest(
	@Min(0) @Max(5) int selfLevel,
	@Size(max = 1000) String reason
) { }
