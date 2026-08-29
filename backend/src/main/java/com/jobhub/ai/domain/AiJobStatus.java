package com.jobhub.ai.domain;

/**
 * AI 任务状态机（PRD 9.2）：
 * QUEUED -> RUNNING -> SUCCEEDED / FAILED；QUEUED、RUNNING -> CANCELED；
 * FAILED -> QUEUED（重试，attempt_count + 1，上限 3）。完成类转移均以单次 WHERE 守卫保证幂等。
 */
public enum AiJobStatus {
	QUEUED,
	RUNNING,
	SUCCEEDED,
	FAILED,
	CANCELED
}
