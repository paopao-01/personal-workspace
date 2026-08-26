package com.jobhub.integration;

import com.jobhub.integration.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class InterviewIntegrationTest extends AbstractIntegrationTest {
    @Test void AT10_createFirstInterview_promotesApplicationAndCreatesReminders(){
        String app=createApplicationAt("RESUME_PASSED");
        ResponseEntity<String> r=createInterview(app,"2026-09-10T10:00:00Z");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(JsonProbe.str(r.getBody(),"scheduleStatus")).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("select status from application_record where id=?",String.class,app)).isEqualTo("INTERVIEWING");
        assertThat(jdbc.queryForObject("select count(*) from application_status_log where application_id=? and from_status='RESUME_PASSED' and to_status='INTERVIEWING'",Integer.class,app)).isEqualTo(1);
        assertThat(JsonProbe.arraySize(getReminders(JsonProbe.str(r.getBody(),"id")),"" )).isEqualTo(3);
    }

    @Test void AT11_reschedule_cancelsPendingAndPreservesSent(){
        String id=JsonProbe.str(createInterview(createApplicationAt("RESUME_PASSED"),"2026-09-10T10:00:00Z").getBody(),"id");
        String sent=jdbc.queryForObject("select id from interview_reminder where interview_id=? limit 1",String.class,id);
        jdbc.update("update interview_reminder set status='SENT' where id=?",sent);
        String body=readInterview(id); long version=JsonProbe.lng(body,"version");
        ResponseEntity<String> r=restTemplate.exchange(url("/interviews/"+id+"/reschedule"),HttpMethod.POST,TestFixtures.httpWithHeaders("{\"startsAt\":\"2026-09-11T12:00:00Z\",\"eventTimeZone\":\"Asia/Shanghai\"}","If-Match-Version",String.valueOf(version)),String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("select count(*) from interview_reminder where interview_id=? and status='SENT'",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from interview_reminder where interview_id=? and status='CANCELED'",Integer.class,id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from interview_reminder where interview_id=? and status='PENDING'",Integer.class,id)).isEqualTo(3);
    }

    @Test void AT12_cancelAndNoShow_keepPendingResultAndRejectResultUpdate(){
        String id=JsonProbe.str(createInterview(createApplicationAt("RESUME_PASSED"),"2026-09-10T10:00:00Z").getBody(),"id");
        long v=JsonProbe.lng(readInterview(id),"version");
        ResponseEntity<String> noShow=restTemplate.exchange(url("/interviews/"+id+"/no-show"),HttpMethod.POST,TestFixtures.httpWithHeaders("{}","If-Match-Version",String.valueOf(v)),String.class);
        assertThat(noShow.getStatusCode()).isEqualTo(HttpStatus.OK); assertThat(JsonProbe.str(noShow.getBody(),"scheduleStatus")).isEqualTo("NO_SHOW"); assertThat(JsonProbe.str(noShow.getBody(),"result")).isEqualTo("PENDING");
        ResponseEntity<String> update=restTemplate.exchange(url("/interviews/"+id),HttpMethod.PUT,TestFixtures.httpWithHeaders("{\"result\":\"PASSED\"}","If-Match-Version",String.valueOf(JsonProbe.lng(noShow.getBody(),"version"))),String.class);
        assertThat(update.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test void AT13_pastInterview_staysScheduled(){
        String id=JsonProbe.str(createInterview(createApplicationAt("RESUME_PASSED"),"2020-09-10T10:00:00Z").getBody(),"id");
        assertThat(JsonProbe.str(readInterview(id),"scheduleStatus")).isEqualTo("SCHEDULED");
    }

    @Test void AT14_pastReminder_remainsPendingForInAppDisplay(){
        String id=JsonProbe.str(createInterview(createApplicationAt("RESUME_PASSED"),"2020-09-10T10:00:00Z").getBody(),"id");
        String reminders=getReminders(id);
        assertThat(JsonProbe.arraySize(reminders,"" )).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from interview_reminder where interview_id=? and status='PENDING'",Integer.class,id)).isEqualTo(3);
    }

    @Test void interviewList_filtersByApplicationStatus_andReturnsApplicationSummary(){
        String rejectedApplication=createApplicationAt("RESUME_PASSED");
        createInterview(rejectedApplication,"2026-09-10T10:00:00Z");
        transition(rejectedApplication,"REJECTED","3");
        String interviewingApplication=createApplicationAt("RESUME_PASSED");
        createInterview(interviewingApplication,"2026-09-11T10:00:00Z");

        String body=restTemplate.getForEntity(url("/interviews?from=2026-09-01T00:00:00Z&to=2026-09-30T23:59:59Z&applicationStatus=INTERVIEWING"),String.class).getBody();

        assertThat(JsonProbe.arraySize(body,"")).isEqualTo(1);
        assertThat(JsonProbe.arrStr(body,"",0,"application.id")).isEqualTo(interviewingApplication);
        assertThat(JsonProbe.arrStr(body,"",0,"application.status")).isEqualTo("INTERVIEWING");
        assertThat(JsonProbe.arrStr(body,"",0,"application.companyName")).isEqualTo("示例科技");
        assertThat(JsonProbe.arrStr(body,"",0,"application.jobTitle")).isEqualTo("Java 后端工程师");
    }

    private ResponseEntity<String> createInterview(String app,String starts){String b="{\"applicationId\":\""+app+"\",\"roundName\":\"一面\",\"startsAt\":\""+starts+"\",\"eventTimeZone\":\"Asia/Shanghai\",\"preparationChecklist\":[\"准备项目案例\"]}";return restTemplate.exchange(url("/interviews"),HttpMethod.POST,TestFixtures.httpWithHeaders(b,"Idempotency-Key",TestFixtures.newKey()),String.class);}
    private String createApplicationAt(String status){String job=JsonProbe.str(restTemplate.postForEntity(url("/jobs"),TestFixtures.httpJson(TestFixtures.createJobBody("示例科技","Java 后端工程师")),String.class).getBody(),"id");String app=JsonProbe.str(restTemplate.exchange(url("/applications"),HttpMethod.POST,TestFixtures.httpWithHeaders(TestFixtures.createApplicationBody(job,"2026-08-20","BOSS直聘",null,null,null),"Idempotency-Key",TestFixtures.newKey()),String.class).getBody(),"id");transition(app,"APPLIED","0");if(status.equals("RESUME_PASSED")||status.equals("INTERVIEWING"))transition(app,"RESUME_PASSED","1");if(status.equals("INTERVIEWING"))transition(app,"INTERVIEWING","2");return app;}
    private void transition(String app,String target,String v){restTemplate.exchange(url("/applications/"+app+"/transition"),HttpMethod.POST,TestFixtures.httpWithHeaders(TestFixtures.transitionBody(target,null,null),"Idempotency-Key",TestFixtures.newKey(),"If-Match-Version",v),String.class);}
    private String readInterview(String id){return restTemplate.getForEntity(url("/interviews/"+id),String.class).getBody();}
    private String getReminders(String id){return restTemplate.getForEntity(url("/interviews/"+id+"/reminders"),String.class).getBody();}
}
