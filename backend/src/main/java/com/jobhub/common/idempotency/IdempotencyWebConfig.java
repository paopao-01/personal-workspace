package com.jobhub.common.idempotency;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class IdempotencyWebConfig implements WebMvcConfigurer {

	private final IdempotencyInterceptor idempotencyInterceptor;

	public IdempotencyWebConfig(IdempotencyInterceptor idempotencyInterceptor) {
		this.idempotencyInterceptor = idempotencyInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		// 只对写操作路径启用，避免无意义开销
		registry.addInterceptor(idempotencyInterceptor)
				.addPathPatterns("/api/**");
	}
}
