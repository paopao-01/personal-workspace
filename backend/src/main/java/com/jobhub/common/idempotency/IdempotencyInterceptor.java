package com.jobhub.common.idempotency;

import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.idempotency.infrastructure.IdempotencyRecordMapper;
import com.jobhub.common.time.UtcTime;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * 写操作幂等拦截器。
 *
 * 操作名 = HTTP method + "|" + 控制器类全名 + "#" + 方法名 + "|" + URI 模板。
 * 请求指纹 = SHA-256(method + sorted query + body bytes) 的 hex。
 *
 * 命中规则：
 *   - key + operation 命中且 fingerprint 一致 → 原样回放 status/body 并 return false
 *   - 命中但 fingerprint 不一致 → 抛 IdempotencyConflictException（409）
 *   - 未命中 → 放行；postHandle 时将响应状态+body 写入 idempotency_record
 *
 * 只对带 Idempotency-Key 头的请求生效；缺失则放行（由后续校验或 GlobalExceptionHandler 兜底）。
 */
@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(IdempotencyInterceptor.class);
	private static final String HEADER = "Idempotency-Key";
	private static final Duration TTL = Duration.ofHours(24);

	private static final String CTX_KEY = "__idem_record_pending";
	private static final String BODY_KEY = "__idem_body_bytes";

	private final IdempotencyRecordMapper mapper;
	private final IdGenerator idGenerator;
	private final UtcTime utcTime;

	public IdempotencyInterceptor(IdempotencyRecordMapper mapper, IdGenerator idGenerator, UtcTime utcTime) {
		this.mapper = mapper;
		this.idGenerator = idGenerator;
		this.utcTime = utcTime;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		if (!(handler instanceof HandlerMethod hm)) {
			return true;
		}
		String key = request.getHeader(HEADER);
		if (key == null || key.isBlank()) {
			return true;
		}

		String operation = operationName(request, hm);
		IdempotencyRecord existing = mapper.selectByKey(key, operation);
		if (existing != null) {
			String currentFp = computeFingerprint(request);
			if (!existing.requestFingerprint().equals(currentFp)) {
				throw new com.jobhub.common.error.IdempotencyConflictException(
						"Idempotency-Key " + key + " was already used for a different request body");
			}
			response.setStatus(existing.responseStatus());
			response.setContentType("application/json;charset=UTF-8");
			byte[] body = existing.responseBodyJson().getBytes(StandardCharsets.UTF_8);
			response.getOutputStream().write(body);
			response.getOutputStream().flush();
			return false;
		}

		byte[] bodyBytes = request instanceof CachedBodyHttpServletRequest cb
				? cb.cachedBody()
				: new byte[0];
		request.setAttribute(BODY_KEY, bodyBytes);
		request.setAttribute(CTX_KEY, new PendingRecord(key, operation, computeFingerprintFromBytes(bodyBytes, request)));
		return true;
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
						   ModelAndView mv) throws Exception {
		Object pendingObj = request.getAttribute(CTX_KEY);
		if (!(pendingObj instanceof PendingRecord pending)) {
			return;
		}
		byte[] bodyBytes = (byte[]) request.getAttribute(BODY_KEY);
		String responseBodyJson;
		int status;
		if (response instanceof ContentCachingResponseWrapper wrapper) {
			byte[] respBody = wrapper.getContentAsByteArray();
			responseBodyJson = new String(respBody == null ? new byte[0] : respBody, StandardCharsets.UTF_8);
			status = wrapper.getStatus();
		} else {
			responseBodyJson = "";
			status = response.getStatus();
		}
		String now = utcTime.now();
		IdempotencyRecord rec = new IdempotencyRecord(
				idGenerator.newId(),
				pending.key(),
				pending.operation(),
				pending.fingerprint(),
				status,
				responseBodyJson,
				now,
				Instant.now().plus(TTL).toString()
		);
		try {
			mapper.insert(rec);
		} catch (Exception ex) {
			IdempotencyRecord concurrent = mapper.selectByKey(pending.key(), pending.operation());
			if (concurrent != null && !concurrent.requestFingerprint().equals(pending.fingerprint())) {
				throw new com.jobhub.common.error.IdempotencyConflictException(
						"Idempotency-Key " + pending.key() + " was used for a different request body");
			}
			log.debug("Concurrent idempotency insert race; ignored");
		}
	}

	private String operationName(HttpServletRequest req, HandlerMethod hm) {
		return req.getMethod() + "|" + hm.getMethod().getDeclaringClass().getName() + "#" + hm.getMethod().getName();
	}

	private String computeFingerprint(HttpServletRequest req) {
		byte[] bodyBytes = req instanceof CachedBodyHttpServletRequest cb ? cb.cachedBody() : new byte[0];
		return computeFingerprintFromBytes(bodyBytes, req);
	}

	private String computeFingerprintFromBytes(byte[] bodyBytes, HttpServletRequest req) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(req.getMethod().getBytes(StandardCharsets.UTF_8));
			md.update((byte) '|');
			String sortedQuery = req.getParameterMap().entrySet().stream()
					.sorted(java.util.Map.Entry.comparingByKey())
					.map(e -> e.getKey() + "=" + String.join(",", e.getValue()))
					.collect(Collectors.joining("&"));
			md.update(sortedQuery.getBytes(StandardCharsets.UTF_8));
			md.update((byte) '|');
			md.update(bodyBytes);
			StringBuilder sb = new StringBuilder();
			for (byte b : md.digest()) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

	record PendingRecord(String key, String operation, String fingerprint) { }
}
