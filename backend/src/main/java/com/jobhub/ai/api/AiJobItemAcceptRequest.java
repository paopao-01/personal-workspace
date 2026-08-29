package com.jobhub.ai.api;

/**
 * 采纳请求：payload 可选（开放对象，按任务类型区分结构）；省略表示按原文采纳。
 */
public record AiJobItemAcceptRequest(com.fasterxml.jackson.databind.JsonNode payload) { }
