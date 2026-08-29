package com.jobhub.datamanagement.api;

import com.jobhub.datamanagement.application.NotificationService;
import com.jobhub.datamanagement.domain.Notification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class NotificationController {
	private final NotificationService service;

	public NotificationController(NotificationService service) {
		this.service = service;
	}

	@GetMapping("/notifications")
	public List<NotificationResponse> list() {
		return service.list().stream().map(NotificationResponse::from).toList();
	}

	@PostMapping("/notifications/{notificationId}/read")
	public ResponseEntity<NotificationResponse> markRead(@PathVariable String notificationId) {
		Notification notification = service.markRead(notificationId);
		return ResponseEntity.ok(NotificationResponse.from(notification));
	}
}
