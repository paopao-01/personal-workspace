package com.jobhub.skill.api;

import com.jobhub.skill.application.SkillProfileService;
import com.jobhub.skill.domain.SkillProfile;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class SkillController {
	private final SkillProfileService service;

	public SkillController(SkillProfileService service) {
		this.service = service;
	}

	@GetMapping("/skills/profile")
	public List<SkillProfileResponse> profile() {
		return service.list().stream().map(SkillProfileResponse::from).toList();
	}

	@PutMapping("/skills/{skillId}/self-level")
	public ResponseEntity<SkillProfileResponse> updateSelfLevel(@PathVariable String skillId,
			@RequestHeader(value = "If-Match-Version", required = false) Long version,
			@Valid @RequestBody SelfLevelUpdateRequest request) {
		if (version == null) {
			return ResponseEntity.badRequest().build();
		}
		SkillProfile profile = service.updateSelfLevel(skillId, version, request.selfLevel());
		return ResponseEntity.ok(SkillProfileResponse.from(profile));
	}
}
