package com.jobhub.ai.api;

import com.jobhub.ai.domain.AiItemPayload;

/**
 * 采纳请求：payload 可选；省略表示按原文采纳，提供时按字段合并覆盖。
 */
public record AiJobItemAcceptRequest(AiItemPayload payload) { }
