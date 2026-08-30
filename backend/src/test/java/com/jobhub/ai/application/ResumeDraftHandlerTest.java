package com.jobhub.ai.application;

import com.jobhub.ai.domain.AiJob;
import com.jobhub.ai.domain.AiItemPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeDraftHandlerTest {
	@Test
	void executeReturnsOneDraftCandidateAndKeepsOnlyModelText() {
		ResumeDraftHandler handler = new ResumeDraftHandler();
		AiJob job = new AiJob();
		job.setInputSnapshot("USER_CONFIRMED_RESUME: Java developer\nJOB_DESCRIPTION: Spring Boot");
		AiChatClient client = (provider, systemPrompt, userPrompt) -> "[ {\"type\":\"DRAFT\",\"rawText\":\"  Java developer\\n\\nSpring Boot  \"} ]";

		List<AiItemPayload> result = handler.execute(job, null, client);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).type()).isEqualTo("DRAFT");
		assertThat(result.get(0).rawText()).isEqualTo("Java developer\n\nSpring Boot");
		assertThat(handler.buildSystemPrompt()).contains("不得新增未确认");
	}
}
