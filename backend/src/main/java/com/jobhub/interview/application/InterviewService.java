package com.jobhub.interview.application;

import com.jobhub.application.application.ApplicationService;
import com.jobhub.application.domain.Application;
import com.jobhub.application.domain.ApplicationStatus;
import com.jobhub.application.application.ApplicationTransitionCommand;
import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.common.version.VersionCheck;
import com.jobhub.datamanagement.application.SettingsService;
import com.jobhub.datamanagement.application.TrashService;
import com.jobhub.interview.domain.*;
import com.jobhub.interview.infrastructure.*;
import com.jobhub.interview.api.InterviewUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class InterviewService {
    private final InterviewMapper interviewMapper; private final ChecklistMapper checklistMapper; private final ReminderMapper reminderMapper;
    private final ApplicationService applicationService; private final SettingsService settingsService; private final TrashService trashService; private final IdGenerator ids; private final UtcTime time;
    public InterviewService(InterviewMapper im, ChecklistMapper cm, ReminderMapper rm, ApplicationService as, SettingsService ss, TrashService ts, IdGenerator ids, UtcTime time){this.interviewMapper=im;this.checklistMapper=cm;this.reminderMapper=rm;this.applicationService=as;this.settingsService=ss;this.trashService=ts;this.ids=ids;this.time=time;}

    @Transactional
    public Interview create(String applicationId,String roundName,String startsAt,String zone,InterviewMode mode,String address,String contact,List<String> checklist,String notes){
        Application app=applicationService.get(applicationId);
        if(app.getStatus()!=ApplicationStatus.RESUME_PASSED && app.getStatus()!=ApplicationStatus.INTERVIEWING) throw new BusinessRuleException("Interview can only be created for RESUME_PASSED or INTERVIEWING application");
        String normalized=normalizeInstant(startsAt), now=time.now(), id=ids.newId();
        if(app.getStatus()==ApplicationStatus.RESUME_PASSED) applicationService.transition(applicationId,app.getVersion(),new ApplicationTransitionCommand(ApplicationStatus.INTERVIEWING,"创建首场面试",false),null);
        Interview i=Interview.create(id,applicationId,roundName,normalized,zone,mode,address,contact,notes,now); interviewMapper.insert(i); saveChecklist(id,checklist,now); createDefaultReminders(id,normalized,now); return interviewMapper.selectById(id);
    }
    public Interview get(String id){Interview i=interviewMapper.selectById(id);VersionCheck.requireFound(i,"Interview",id);return i;}
    public List<String> checklist(String id){get(id);return checklistMapper.selectTexts(id);}
    public List<Interview> list(String from,String to,InterviewScheduleStatus status){return interviewMapper.selectUpcoming(from==null?"0000-01-01T00:00:00Z":normalizeInstant(from),to==null?"9999-12-31T23:59:59Z":normalizeInstant(to),status);}
    public List<InterviewListItem> listItems(String from,String to,InterviewScheduleStatus scheduleStatus,ApplicationStatus applicationStatus,InterviewMode mode){return interviewMapper.selectListItems(from==null?"0000-01-01T00:00:00Z":normalizeInstant(from),to==null?"9999-12-31T23:59:59Z":normalizeInstant(to),scheduleStatus,applicationStatus,mode);}
    public List<Interview> byApplication(String appId){return interviewMapper.selectByApplication(appId);}
    @Transactional public Interview update(String id,long expected,InterviewUpdateRequest v){Interview i=get(id);i.updateMeta(v.roundName(),v.mode(),v.meetingUrlOrAddress(),v.contact(),v.notes(),v.result(),time.now());int n=interviewMapper.updateMeta(i,expected);VersionCheck.requireAffected(n,i.getVersion());VersionCheck.requireAffected(interviewMapper.bumpVersion(id,expected),i.getVersion());if(v.preparationChecklist()!=null){checklistMapper.deleteByInterview(id);saveChecklist(id,v.preparationChecklist(),time.now());}return get(id);}
    @Transactional public Interview complete(String id,long expected,InterviewResult result){Interview i=get(id);i.complete(result,time.now());persistState(i,expected);reminderMapper.cancelOpen(id,time.now());return get(id);}
    @Transactional public Interview cancel(String id,long expected){Interview i=get(id);i.cancel(time.now());persistState(i,expected);reminderMapper.cancelOpen(id,time.now());return get(id);}
    @Transactional public Interview noShow(String id,long expected){Interview i=get(id);i.noShow(time.now());persistState(i,expected);reminderMapper.cancelOpen(id,time.now());return get(id);}
    @Transactional public Interview reschedule(String id,long expected,String startsAt,String zone){Interview i=get(id);i.reschedule(normalizeInstant(startsAt),zone,time.now());persistSchedule(i,expected);reminderMapper.cancelOpen(id,time.now());createDefaultReminders(id,i.getStartsAt(),time.now());return get(id);}
    @Transactional public void delete(String id,long expected){Interview i=get(id);String now=time.now();VersionCheck.requireAffected(interviewMapper.softDelete(id,expected,now),i.getVersion());reminderMapper.cancelOpen(id,now);trashService.recordDeletion(TrashService.TYPE_INTERVIEW,id,i.getRoundName(),List.of("复盘与问题记录保留"),now);}
    public List<Reminder> reminders(String interviewId){get(interviewId);return reminderMapper.selectByInterview(interviewId);}
    @Transactional public Reminder createReminder(String interviewId,ReminderType type,String scheduledAt){get(interviewId);Reminder r=Reminder.create(ids.newId(),interviewId,type,normalizeInstant(scheduledAt),time.now());reminderMapper.insert(r);return r;}
    @Transactional public Reminder updateReminder(String id,long expected,String scheduledAt,Boolean enabled){Reminder r=reminderMapper.selectById(id);VersionCheck.requireFound(r,"Reminder",id);if(r.getStatus()==ReminderStatus.SENT)throw new BusinessRuleException("SENT reminder cannot be edited");r.update(scheduledAt==null?null:normalizeInstant(scheduledAt),enabled==null||enabled,time.now());VersionCheck.requireAffected(reminderMapper.update(r,expected),r.getVersion());return reminderMapper.selectById(id);}
    @Transactional public Reminder retryReminder(String id,long expected){Reminder r=reminderMapper.selectById(id);VersionCheck.requireFound(r,"Reminder",id);VersionCheck.requireAffected(r.getVersion()==expected?1:0,r.getVersion());if(r.getStatus()!=ReminderStatus.FAILED)throw new BusinessRuleException("Only FAILED reminder can be retried");VersionCheck.requireAffected(reminderMapper.retryFailed(id,expected,time.now()),r.getVersion());return reminderMapper.selectById(id);}
    private void persistState(Interview i,long expected){VersionCheck.requireAffected(interviewMapper.updateState(i,expected),i.getVersion());VersionCheck.requireAffected(interviewMapper.bumpVersion(i.getId(),expected),i.getVersion());}
    private void persistSchedule(Interview i,long expected){VersionCheck.requireAffected(interviewMapper.updateSchedule(i,expected),i.getVersion());VersionCheck.requireAffected(interviewMapper.bumpVersion(i.getId(),expected),i.getVersion());}
    private void saveChecklist(String id,List<String> values,String now){if(values!=null)for(int n=0;n<values.size();n++)if(values.get(n)!=null&&!values.get(n).isBlank())checklistMapper.insert(ids.newId(),id,values.get(n),n,now);}
    private void createDefaultReminders(String id,String startsAt,String now){Instant at=Instant.parse(startsAt);for(Integer minutes:settingsService.defaultReminderOffsetsMinutes()){ReminderType type=switch(minutes){case 1440->ReminderType.ONE_DAY;case 120->ReminderType.TWO_HOURS;case 30->ReminderType.THIRTY_MINUTES;default->ReminderType.CUSTOM;};reminderMapper.insert(Reminder.create(ids.newId(),id,type,at.minus(Duration.ofMinutes(minutes)).toString(),now));}}
    private String normalizeInstant(String value){try{return Instant.parse(value).toString();}catch(DateTimeParseException ex){throw new BusinessRuleException("Time must be an ISO-8601 instant with UTC offset");}}
}
