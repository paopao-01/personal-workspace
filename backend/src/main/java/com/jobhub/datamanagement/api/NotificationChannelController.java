package com.jobhub.datamanagement.api;

import com.jobhub.datamanagement.application.EmailChannelConfig;
import com.jobhub.datamanagement.application.NotificationChannelService;
import com.jobhub.datamanagement.domain.ChannelDelivery;
import com.jobhub.datamanagement.domain.ChannelType;
import com.jobhub.datamanagement.domain.Notification;
import com.jobhub.datamanagement.domain.NotificationChannel;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class NotificationChannelController {
	private final NotificationChannelService channelService;
	private final com.jobhub.datamanagement.application.NotificationService notificationService;

	public NotificationChannelController(NotificationChannelService channelService,
			com.jobhub.datamanagement.application.NotificationService notificationService) {
		this.channelService = channelService;
		this.notificationService = notificationService;
	}

	@GetMapping("/notification-channels/{channelType}")
	public NotificationChannelResponse get(@PathVariable ChannelType channelType) {
		return NotificationChannelResponse.from(channelService.get(channelType));
	}

	@PutMapping("/notification-channels/{channelType}")
	public ResponseEntity<NotificationChannelResponse> update(@PathVariable ChannelType channelType,
			@RequestHeader(value = "If-Match-Version", required = false) Long version,
			@Valid @RequestBody NotificationChannelUpdateRequest request) {
		if (version == null) {
			return ResponseEntity.badRequest().build();
		}
		EmailChannelConfig config = request.config() == null ? null : request.config().toEmailConfig();
		NotificationChannel channel = channelService.update(channelType, version,
			Boolean.TRUE.equals(request.enabled()), config);
		return ResponseEntity.ok(NotificationChannelResponse.from(channel));
	}

	@PostMapping("/notification-channels/{channelType}/test")
	public ResponseEntity<ChannelTestResultResponse> test(@PathVariable ChannelType channelType) {
		ChannelDelivery delivery = channelService.test(channelType);
		Notification notification = notificationService.get(delivery.getNotificationId());
		return ResponseEntity.ok(ChannelTestResultResponse.from(channelType, notification.getId(),
			notification.getDeliveries()));
	}

	@PostMapping("/notifications/{notificationId}/channel-deliveries/{channelType}/ack")
	public ResponseEntity<Void> ack(@PathVariable String notificationId, @PathVariable ChannelType channelType) {
		if (channelType != ChannelType.BROWSER) {
			throw new com.jobhub.common.error.BusinessRuleException("仅 BROWSER 渠道支持回执");
		}
		channelService.ackBrowserDelivery(notificationId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/notification-channels")
	public List<NotificationChannelResponse> list() {
		return channelService.list().stream().map(NotificationChannelResponse::from).toList();
	}
}
