package com.jobhub.interview.api;
import com.jobhub.interview.domain.ReminderType;
import jakarta.validation.constraints.NotBlank;
public record ReminderCreateRequest(ReminderType reminderType,@NotBlank String scheduledAt) { }
