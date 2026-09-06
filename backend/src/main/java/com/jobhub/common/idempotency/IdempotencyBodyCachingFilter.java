package com.jobhub.common.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * 对带 Idempotency-Key 头的写操作请求：
 *   - 用 CachedBodyHttpServletRequest 包装请求体（可多次读取）
 *   - 用 ContentCachingResponseWrapper 包装响应（postHandle 阶段读取 body 后再写回客户端）
 * 排序最高优先级，先于 DispatcherServlet 与其他业务 Filter。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IdempotencyBodyCachingFilter extends OncePerRequestFilter {

	private static final String HEADER = "Idempotency-Key";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String key = request.getHeader(HEADER);
		// multipart 请求不缓存原始请求体：缓存会消费输入流，破坏后续 getParts()/MultipartFile 解析。
		// 恢复端点等 multipart 写操作由业务层（如 ImportService.restore 行级幂等）保证语义幂等，
		// 幂等记录的请求指纹不含 multipart body（可接受：前端每次调用生成新 key，不依赖回放）。
		if (key != null && !key.isBlank() && hasBody(request) && !isMultipart(request)) {
			CachedBodyHttpServletRequest wrappedReq = new CachedBodyHttpServletRequest(request);
			ContentCachingResponseWrapper wrappedResp = new ContentCachingResponseWrapper(response);
			try {
				filterChain.doFilter(wrappedReq, wrappedResp);
			} finally {
				wrappedResp.copyBodyToResponse();
			}
		} else {
			filterChain.doFilter(request, response);
		}
	}

	private boolean hasBody(HttpServletRequest request) {
		String method = request.getMethod();
		return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
				|| "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);
	}

	private boolean isMultipart(HttpServletRequest request) {
		String contentType = request.getContentType();
		return contentType != null && contentType.toLowerCase().startsWith("multipart/");
	}
}
