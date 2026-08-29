package com.jobhub.datamanagement.api;

import com.jobhub.datamanagement.domain.ChannelDelivery;
import com.jobhub.datamanagement.domain.ChannelType;

public record ChannelTestResultResponse(
	String channelType,
	String notificationId,
	java.util.List<ChannelDeliveryResponse> deliveries
) {
	public static ChannelTestResultResponse from(ChannelType channelType, String notificationId,
			java.util.List<ChannelDelivery> deliveries) {
		return new ChannelTestResultResponse(
			channelType.name(),
			notificationId,
			deliveries.stream().map(ChannelDeliveryResponse::from).toList()
		);
	}
}
