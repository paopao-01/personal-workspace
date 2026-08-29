package com.jobhub.ai.domain;

/**
 * 简历定制建议条目载荷（PRD 9.4）：sourceId 指向已确认的事实（项目/技能），
 * suggestedText 为面向目标岗位的重写表达；由处理器校验 sourceId 必须来自输入事实清单。
 */
public record ResumeSuggestion(String sourceType, String sourceId, String sourceTitle, String suggestedText) { }
