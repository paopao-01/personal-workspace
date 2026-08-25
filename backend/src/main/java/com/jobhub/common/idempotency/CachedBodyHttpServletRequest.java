package com.jobhub.common.idempotency;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 缓存请求体，允许在拦截器中多次读取。
 * 仅对 Idempotency 拦截器路径启用，避免对所有请求做缓存。
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

	private final byte[] cachedBody;

	public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
		super(request);
		this.cachedBody = request.getInputStream().readAllBytes();
	}

	public byte[] cachedBody() {
		return cachedBody;
	}

	@Override
	public ServletInputStream getInputStream() {
		return new CachedServletInputStream(cachedBody);
	}

	@Override
	public BufferedReader getReader() {
		return new BufferedReader(new InputStreamReader(
				new ByteArrayInputStream(cachedBody), StandardCharsets.UTF_8));
	}

	private static class CachedServletInputStream extends ServletInputStream {
		private final ByteArrayInputStream delegate;

		CachedServletInputStream(byte[] body) {
			this.delegate = new ByteArrayInputStream(body);
		}

		@Override
		public boolean isFinished() {
			return delegate.available() == 0;
		}

		@Override
		public boolean isReady() {
			return true;
		}

		@Override
		public void setReadListener(ReadListener readListener) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int read() {
			return delegate.read();
		}
	}
}
