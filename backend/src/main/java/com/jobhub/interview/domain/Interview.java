package com.jobhub.interview.domain;

import com.jobhub.common.error.IllegalStateTransitionException;

public class Interview {
    private String id;
    private String applicationId;
    private String roundName;
    private String startsAt;
    private String eventTimeZone;
    private InterviewMode mode;
    private String meetingUrlOrAddress;
    private String contact;
    private InterviewScheduleStatus scheduleStatus;
    private InterviewResult result;
    private String notes;
    private String createdAt;
    private String updatedAt;
    private long version;

    public Interview() { }

    public static Interview create(String id, String applicationId, String roundName, String startsAt,
                                    String eventTimeZone, InterviewMode mode, String meetingUrlOrAddress,
                                    String contact, String notes, String now) {
        Interview i = new Interview();
        i.id = id; i.applicationId = applicationId; i.roundName = roundName; i.startsAt = startsAt;
        i.eventTimeZone = eventTimeZone; i.mode = mode; i.meetingUrlOrAddress = meetingUrlOrAddress;
        i.contact = contact; i.scheduleStatus = InterviewScheduleStatus.SCHEDULED;
        i.result = InterviewResult.PENDING; i.notes = notes; i.createdAt = now; i.updatedAt = now;
        return i;
    }

    public void updateMeta(String roundName, InterviewMode mode, String meetingUrlOrAddress,
                           String contact, String notes, InterviewResult result, String now) {
        if (scheduleStatus != InterviewScheduleStatus.COMPLETED && result != null && result != InterviewResult.PENDING) {
            throw new IllegalStateTransitionException(scheduleStatus.name(), "RESULT_" + result.name(),
                    "only a completed interview may have PASSED or FAILED result");
        }
        if (scheduleStatus == InterviewScheduleStatus.CANCELED || scheduleStatus == InterviewScheduleStatus.NO_SHOW) {
            if (result != null && result != InterviewResult.PENDING) {
                throw new IllegalStateTransitionException(scheduleStatus.name(), "RESULT_" + result.name(),
                        "canceled or no-show interview must keep result PENDING");
            }
        }
        this.roundName = roundName; this.mode = mode; this.meetingUrlOrAddress = meetingUrlOrAddress;
        this.contact = contact; this.notes = notes;
        if (result != null) this.result = result;
        this.updatedAt = now;
    }

    public void complete(InterviewResult result, String now) {
        requireScheduled("complete");
        this.scheduleStatus = InterviewScheduleStatus.COMPLETED;
        this.result = result == null ? InterviewResult.PENDING : result;
        this.updatedAt = now;
    }

    public void cancel(String now) { requireScheduled("cancel"); this.scheduleStatus = InterviewScheduleStatus.CANCELED; this.result = InterviewResult.PENDING; this.updatedAt = now; }
    public void noShow(String now) { requireScheduled("no-show"); this.scheduleStatus = InterviewScheduleStatus.NO_SHOW; this.result = InterviewResult.PENDING; this.updatedAt = now; }
    public void reschedule(String startsAt, String eventTimeZone, String now) { requireScheduled("reschedule"); this.startsAt = startsAt; this.eventTimeZone = eventTimeZone; this.updatedAt = now; }
    private void requireScheduled(String action) {
        if (scheduleStatus != InterviewScheduleStatus.SCHEDULED) {
            throw new IllegalStateTransitionException(scheduleStatus.name(), action, "only SCHEDULED interview can be changed");
        }
    }
    public String getId() { return id; }
    public String getApplicationId() { return applicationId; }
    public String getRoundName() { return roundName; }
    public String getStartsAt() { return startsAt; }
    public String getEventTimeZone() { return eventTimeZone; }
    public InterviewMode getMode() { return mode; }
    public String getMeetingUrlOrAddress() { return meetingUrlOrAddress; }
    public String getContact() { return contact; }
    public InterviewScheduleStatus getScheduleStatus() { return scheduleStatus; }
    public InterviewResult getResult() { return result; }
    public String getNotes() { return notes; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
