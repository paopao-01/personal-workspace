package com.jobhub.evidence.domain;

/**
 * 证据类型，与 OpenAPI EvidenceCreateRequest.type 一一对应。
 */
public enum EvidenceType {
	PROJECT_CODE,
	GIT_REPOSITORY,
	ARTICLE,
	ARCHITECTURE_DIAGRAM,
	API_DOCUMENT,
	LOAD_TEST_REPORT,
	LOG_OR_MONITORING,
	INTERVIEW_ANSWER,
	WORK_EXPERIENCE
}
