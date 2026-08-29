package com.jobhub.ai.api;

import com.jobhub.ai.application.AiProviderService;

public record AiProviderTestResultResponse(boolean ok, Integer latencyMs, String message) {
	public static AiProviderTestResultResponse from(AiProviderService.AiProviderTestResult result) {
		return new AiProviderTestResultResponse(result.ok(), result.latencyMs(), result.message());
	}
}
