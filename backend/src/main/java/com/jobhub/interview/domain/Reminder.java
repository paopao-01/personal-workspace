package com.jobhub.interview.domain;

public class Reminder {
    private String id;
    private String interviewId;
    private ReminderType reminderType;
    private String scheduledAt;
    private ReminderStatus status;
    private String failureReason;
    private String createdAt;
    private String updatedAt;
    private int attemptCount;
    private long version;
    public Reminder() { }
    public static Reminder create(String id, String interviewId, ReminderType type, String scheduledAt, String now) {
        Reminder r = new Reminder(); r.id=id; r.interviewId=interviewId; r.reminderType=type; r.scheduledAt=scheduledAt;
        r.status=ReminderStatus.PENDING; r.createdAt=now; r.updatedAt=now; return r;
    }
    public void update(String scheduledAt, boolean enabled, String now) {
        if (enabled) this.status = ReminderStatus.PENDING;
        else this.status = ReminderStatus.CANCELED;
        if (scheduledAt != null) this.scheduledAt = scheduledAt;
        this.failureReason = null;
        this.updatedAt = now;
    }
    public String getId(){return id;} public String getInterviewId(){return interviewId;} public ReminderType getReminderType(){return reminderType;}
    public String getScheduledAt(){return scheduledAt;} public ReminderStatus getStatus(){return status;} public String getFailureReason(){return failureReason;}
    public String getCreatedAt(){return createdAt;} public String getUpdatedAt(){return updatedAt;} public int getAttemptCount(){return attemptCount;} public long getVersion(){return version;}
}
