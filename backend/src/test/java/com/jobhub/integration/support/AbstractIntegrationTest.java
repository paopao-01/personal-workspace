package com.jobhub.integration.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 集成测试基类。
 *
 * 选用 @SpringBootTest(RANDOM_PORT) + TestRestTemplate 而非 MockMvc：
 *   - 幂等链路依赖 IdempotencyBodyCachingFilter（Servlet Filter，包装请求体/响应）+
 *     IdempotencyInterceptor（HandlerInterceptor）。MockMvc 默认不经过 Servlet Filter 链，
 *     幂等回放（第二次请求 preHandle 直接写缓存响应并 return false、Filter finally copyBodyToResponse）
 *     在 MockMvc 下不可靠。RANDOM_PORT 走真实 Tomcat，Filter+Interceptor 完整执行。
 *   - 5 个测试类统一 RANDOM_PORT 只启动一次上下文 + 一次 Flyway，比 4 MockMvc + 1 RANDOM_PORT（两次上下文）更省。
 *   - RANDOM_PORT 下服务端在独立线程事务提交，@Transactional 无法回滚其写入，改用 DatabaseCleaner 每方法清表。
 *
 * 非幂等测试省略 Idempotency-Key 头：Filter 无 key 时直通，Interceptor preHandle 无 key 时 return true，
 * 不产生 idempotency_record 副作用。控制器用 @RequestHeader(required=false) 未强制该头。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

	@LocalServerPort
	protected int port;

	@Autowired
	protected TestRestTemplate restTemplate;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected DatabaseCleaner cleaner;

	/**
	 * 每方法清空业务表，保证起点干净、跨方法无串扰。
	 */
	@BeforeEach
	void clearDatabase() {
		cleaner.clearAll();
	}

	/**
	 * 构造完整请求 URL。path 形如 "/jobs"、"/jobs/{id}/archive"，返回 http://127.0.0.1:{port}/api{path}。
	 */
	protected String url(String path) {
		return "http://127.0.0.1:%d/api%s".formatted(port, path);
	}
}
