package com.jobhub.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
		List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
				.collect(Collectors.toList());
		String traceId = newTraceId();
		log.warn("Validation failed traceId={} errors={}", traceId, fieldErrors);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, "Request validation failed", traceId, fieldErrors));
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
		String traceId = newTraceId();
		log.warn("Missing required header traceId={} header={}", traceId, ex.getHeaderName());
		String msg = "Missing required header: " + ex.getHeaderName();
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, msg, traceId,
						List.of(new FieldError(ex.getHeaderName(), "must be present"))));
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
		String traceId = newTraceId();
		log.warn("Resource not found traceId={} type={} id={}", traceId, ex.resourceType(), ex.id());
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ErrorResponse.of(ErrorCode.NOT_FOUND, ex.getMessage(), traceId));
	}

	@ExceptionHandler(VersionConflictException.class)
	public ResponseEntity<ErrorResponse> handleVersionConflict(VersionConflictException ex) {
		String traceId = newTraceId();
		log.warn("Version conflict traceId={} currentVersion={}", traceId, ex.currentVersion());
		ErrorResponse body = new ErrorResponse(
				ErrorCode.VERSION_CONFLICT.code(),
				"Resource version mismatch. Please refresh and retry.",
				traceId,
				null,
				null,
				null,
				"currentVersion=" + ex.currentVersion()
		);
		return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
	}

	@ExceptionHandler(IdempotencyConflictException.class)
	public ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyConflictException ex) {
		String traceId = newTraceId();
		log.warn("Idempotency conflict traceId={}", traceId);
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ErrorResponse.of(ErrorCode.IDEMPOTENCY_CONFLICT, ex.getMessage(), traceId));
	}

	@ExceptionHandler(IllegalStateTransitionException.class)
	public ResponseEntity<ErrorResponse> handleIllegalTransition(IllegalStateTransitionException ex) {
		String traceId = newTraceId();
		log.warn("Illegal state transition traceId={} {} -> {} reason={}",
				traceId, ex.currentState(), ex.targetState(), ex.reason());
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
				.body(ErrorResponse.of(ErrorCode.ILLEGAL_STATE_TRANSITION, ex.getMessage(), traceId,
						ex.currentState(), ex.targetState(), ex.reason()));
	}

	@ExceptionHandler(BusinessRuleException.class)
	public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex) {
		String traceId = newTraceId();
		log.warn("Business rule violation traceId={} code={} msg={}", traceId, ex.errorCode().code(), ex.getMessage());
		return ResponseEntity.status(ex.errorCode().httpStatus())
				.body(ErrorResponse.of(ex.errorCode(), ex.getMessage(), traceId));
	}

	@ExceptionHandler(NoHandlerFoundException.class)
	public ResponseEntity<ErrorResponse> handleNoHandler(NoHandlerFoundException ex) {
		String traceId = newTraceId();
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ErrorResponse.of(ErrorCode.NOT_FOUND, "Endpoint not found", traceId));
	}

	@ExceptionHandler(Throwable.class)
	public ResponseEntity<ErrorResponse> handleAny(Throwable ex, HttpServletRequest req) {
		String traceId = newTraceId();
		log.error("Unhandled exception traceId={} method={} uri={} msg={}",
				traceId, req.getMethod(), req.getRequestURI(), ex.getMessage(), ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, "Internal server error", traceId));
	}

	private static String newTraceId() {
		return UUID.randomUUID().toString();
	}
}
