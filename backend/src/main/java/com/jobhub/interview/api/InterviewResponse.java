package com.jobhub.interview.api;
import com.jobhub.interview.domain.*;
import java.util.List;
public record InterviewResponse(String id,String applicationId,String roundName,String startsAt,String eventTimeZone,InterviewMode mode,String meetingUrlOrAddress,String contact,InterviewScheduleStatus scheduleStatus,InterviewResult result,List<String> preparationChecklist,String notes,long version) {
 public static InterviewResponse from(Interview i,List<String> checklist){return new InterviewResponse(i.getId(),i.getApplicationId(),i.getRoundName(),i.getStartsAt(),i.getEventTimeZone(),i.getMode(),i.getMeetingUrlOrAddress(),i.getContact(),i.getScheduleStatus(),i.getResult(),checklist==null?List.of():checklist,i.getNotes(),i.getVersion());}
}
