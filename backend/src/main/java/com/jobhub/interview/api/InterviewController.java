package com.jobhub.interview.api;

import com.jobhub.application.domain.ApplicationStatus;
import com.jobhub.interview.application.PreparationService;
import com.jobhub.interview.application.InterviewService;
import com.jobhub.interview.domain.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class InterviewController {
    private final InterviewService service;
    private final PreparationService preparationService;
    public InterviewController(InterviewService service, PreparationService preparationService){this.service=service;this.preparationService=preparationService;}
    @GetMapping("/interviews") public List<InterviewListItemResponse> list(@RequestParam(required=false) String from,@RequestParam(required=false) String to,@RequestParam(required=false) InterviewScheduleStatus scheduleStatus,@RequestParam(required=false) ApplicationStatus applicationStatus,@RequestParam(required=false) InterviewMode mode){return service.listItems(from,to,scheduleStatus,applicationStatus,mode).stream().map(i->InterviewListItemResponse.from(i,service.checklist(i.getId()))).toList();}
    @PostMapping("/interviews") public ResponseEntity<InterviewResponse> create(@Valid @RequestBody InterviewCreateRequest r){Interview i=service.create(r.applicationId(),r.roundName(),r.startsAt(),r.eventTimeZone(),r.mode(),r.meetingUrlOrAddress(),r.contact(),r.preparationChecklist(),r.notes());return ResponseEntity.status(HttpStatus.CREATED).body(response(i));}
    @GetMapping("/interviews/upcoming") public List<InterviewResponse> upcoming(@RequestParam(required=false) String from,@RequestParam(required=false) String to,@RequestParam(required=false) InterviewScheduleStatus status){return service.list(from,to,status).stream().map(this::response).toList();}
    @GetMapping("/interviews/{id}") public InterviewResponse get(@PathVariable String id){return response(service.get(id));}
    @GetMapping("/interviews/{id}/preparation") public PreparationPackResponse preparation(@PathVariable String id){return PreparationPackResponse.from(preparationService.getPreparationPack(id));}
    @PutMapping("/interviews/{id}") public ResponseEntity<InterviewResponse> update(@PathVariable String id,@RequestHeader(value="If-Match-Version",required=false) Long version,@Valid @RequestBody InterviewUpdateRequest r){if(version==null)return ResponseEntity.badRequest().build();return ResponseEntity.ok(response(service.update(id,version,r)));}
    @PostMapping("/interviews/{id}/complete") public ResponseEntity<InterviewResponse> complete(@PathVariable String id,@RequestHeader(value="If-Match-Version",required=false) Long version,@RequestBody(required=false) InterviewCompleteRequest r){if(version==null)return ResponseEntity.badRequest().build();return ResponseEntity.ok(response(service.complete(id,version,r==null?InterviewResult.PENDING:r.result())));}
    @PostMapping("/interviews/{id}/cancel") public ResponseEntity<InterviewResponse> cancel(@PathVariable String id,@RequestHeader(value="If-Match-Version",required=false) Long version,@RequestBody(required=false) ReasonRequest ignored){if(version==null)return ResponseEntity.badRequest().build();return ResponseEntity.ok(response(service.cancel(id,version)));}
    @PostMapping("/interviews/{id}/no-show") public ResponseEntity<InterviewResponse> noShow(@PathVariable String id,@RequestHeader(value="If-Match-Version",required=false) Long version,@RequestBody(required=false) ReasonRequest ignored){if(version==null)return ResponseEntity.badRequest().build();return ResponseEntity.ok(response(service.noShow(id,version)));}
    @PostMapping("/interviews/{id}/reschedule") public ResponseEntity<InterviewResponse> reschedule(@PathVariable String id,@RequestHeader(value="If-Match-Version",required=false) Long version,@Valid @RequestBody InterviewRescheduleRequest r){if(version==null)return ResponseEntity.badRequest().build();return ResponseEntity.ok(response(service.reschedule(id,version,r.startsAt(),r.eventTimeZone())));}
    @GetMapping("/interviews/{id}/reminders") public List<ReminderResponse> reminders(@PathVariable String id){return service.reminders(id).stream().map(ReminderResponse::from).toList();}
    @PostMapping("/interviews/{id}/reminders") public ResponseEntity<ReminderResponse> createReminder(@PathVariable String id,@Valid @RequestBody ReminderCreateRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(ReminderResponse.from(service.createReminder(id,r.reminderType(),r.scheduledAt())));}
    @PutMapping("/reminders/{id}") public ResponseEntity<ReminderResponse> updateReminder(@PathVariable String id,@RequestHeader(value="If-Match-Version",required=false) Long version,@Valid @RequestBody ReminderUpdateRequest r){if(version==null)return ResponseEntity.badRequest().build();return ResponseEntity.ok(ReminderResponse.from(service.updateReminder(id,version,r.scheduledAt(),r.enabled())));}
    @PostMapping("/reminders/{id}/retry") public ResponseEntity<ReminderResponse> retryReminder(@PathVariable String id,@RequestHeader(value="If-Match-Version",required=false) Long version){if(version==null)return ResponseEntity.badRequest().build();return ResponseEntity.ok(ReminderResponse.from(service.retryReminder(id,version)));}
    private InterviewResponse response(Interview i){return InterviewResponse.from(i,service.checklist(i.getId()));}
}
