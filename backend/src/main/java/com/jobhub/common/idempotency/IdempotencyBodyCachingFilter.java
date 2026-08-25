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
		if (key != null && !key.isBlank() && hasBody(request)) {
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
}
