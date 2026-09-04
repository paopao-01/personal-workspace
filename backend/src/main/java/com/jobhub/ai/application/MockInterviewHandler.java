package com.jobhub.ai.application;
import com.jobhub.ai.domain.*; import org.springframework.stereotype.Component; import java.util.*;
@Component public class MockInterviewHandler implements AiTaskHandler {
 public AiJobType type(){return AiJobType.MOCK_INTERVIEW;} public String promptVersion(){return "MOCK_INTERVIEW_V1";}
 public String buildSystemPrompt(){return "你是 Java 项目面试官。仅输出 JSON 数组且仅一个元素：{\\\"type\\\":\\\"MOCK_INTERVIEW_OPENING\\\",\\\"rawText\\\":\\\"基于用户项目事实的90秒讲解稿\\\",\\\"rationale\\\":\\\"一个具体高频追问\\\"}。只能使用输入项目快照的事实；不编造指标、技术或职责。";}
 public List<AiItemPayload> execute(AiJob j,AiProvider p,AiChatClient c){var r=AiItemPayload.parseList(c.complete(p,buildSystemPrompt(),j.getInputSnapshot()));if(r.size()!=1||r.get(0).rawText()==null||r.get(0).rawText().isBlank()||r.get(0).rationale()==null||r.get(0).rationale().isBlank())throw new IllegalStateException("模型未返回讲解稿和首个追问");var x=r.get(0);return List.of(new AiItemPayload("MOCK_INTERVIEW_OPENING",x.rawText().trim(),"项目模拟面试",null,x.rationale().trim(),null,null,null,null));}
}
