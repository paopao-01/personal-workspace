package com.jobhub.interview.api;
import com.jobhub.interview.domain.*;
public record ReminderResponse(String id,String interviewId,ReminderType reminderType,String scheduledAt,ReminderStatus status,String failureReason,int attemptCount,long version) { public static ReminderResponse from(Reminder r){return new ReminderResponse(r.getId(),r.getInterviewId(),r.getReminderType(),r.getScheduledAt(),r.getStatus(),r.getFailureReason(),r.getAttemptCount(),r.getVersion());} }
