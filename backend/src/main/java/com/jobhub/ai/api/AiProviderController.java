package com.jobhub.ai.api;

import com.jobhub.ai.application.AiProviderService;
import com.jobhub.ai.domain.AiProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AiProviderController {
	private final AiProviderService service;

	public AiProviderController(AiProviderService service) {
		this.service = service;
	}

	@GetMapping("/ai-providers")
	public List<AiProviderResponse> list() {
		return service.list().stream().map(AiProviderResponse::from).toList();
	}

	@GetMapping("/ai-providers/{providerId}")
	public AiProviderResponse get(@PathVariable String providerId) {
		return AiProviderResponse.from(service.get(providerId));
	}

	@PostMapping("/ai-providers")
	public ResponseEntity<AiProviderResponse> create(@Valid @RequestBody AiProviderUpsertRequest request) {
		AiProvider provider = service.create(request.providerType(), request.name(), request.baseUrl(),
				request.model(), request.apiKey());
		return ResponseEntity.status(HttpStatus.CREATED).body(AiProviderResponse.from(provider));
	}

	@PutMapping("/ai-providers/{providerId}")
	public ResponseEntity<AiProviderResponse> update(@PathVariable String providerId,
			@RequestHeader(value = "If-Match-Version", required = false) Long version,
			@Valid @RequestBody AiProviderUpsertRequest request) {
		if (version == null) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.ok(AiProviderResponse.from(service.update(providerId, version, request.providerType(),
				request.name(), request.baseUrl(), request.model(), request.apiKey())));
	}

	@PostMapping("/ai-providers/{providerId}/activate")
	public AiProviderResponse activate(@PathVariable String providerId) {
		return AiProviderResponse.from(service.activate(providerId));
	}

	@PostMapping("/ai-providers/{providerId}/test")
	public AiProviderTestResultResponse test(@PathVariable String providerId) {
		return AiProviderTestResultResponse.from(service.test(providerId));
	}
}
