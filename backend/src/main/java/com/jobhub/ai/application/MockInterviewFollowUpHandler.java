package com.jobhub.ai.application;
import com.jobhub.ai.domain.*; import org.springframework.stereotype.Component; import java.util.*;
@Component public class MockInterviewFollowUpHandler implements AiTaskHandler {
 public AiJobType type(){return AiJobType.MOCK_INTERVIEW_FOLLOW_UP;} public String promptVersion(){return "MOCK_INTERVIEW_FOLLOW_UP_V1";}
 public String buildSystemPrompt(){return "你是 Java 项目面试官。仅输出 JSON 数组且仅一个元素：{\"type\":\"MOCK_INTERVIEW_FOLLOW_UP\",\"rawText\":\"基于项目快照和用户刚才回答的一道具体追问\"}。只能使用输入项目快照的事实；不编造指标、技术或职责；不要评分、建议或答案。";}
 public List<AiItemPayload> execute(AiJob j,AiProvider p,AiChatClient c){var r=AiItemPayload.parseList(c.complete(p,buildSystemPrompt(),j.getInputSnapshot()));if(r.size()!=1||r.get(0).rawText()==null||r.get(0).rawText().isBlank())throw new IllegalStateException("模型未返回连续追问");return List.of(new AiItemPayload("MOCK_INTERVIEW_FOLLOW_UP",r.get(0).rawText().trim(),"项目模拟面试",null,"连续追问",null,null,null,null));}
}
