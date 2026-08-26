package com.jobhub.interview.api;
import jakarta.validation.constraints.NotBlank;
public record InterviewRescheduleRequest(@NotBlank String startsAt,@NotBlank String eventTimeZone,String reason) { }
