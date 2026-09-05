package com.jobhub.ai.application;

import com.jobhub.ai.domain.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component public class MockInterviewAnswerEvaluationHandler implements AiTaskHandler {
 public AiJobType type(){return AiJobType.MOCK_INTERVIEW_ANSWER_EVALUATION;} public String promptVersion(){return "MOCK_INTERVIEW_ANSWER_EVALUATION_V1";}
 public String buildSystemPrompt(){return "你是 Java 项目面试教练。仅输出 JSON 数组且仅一个元素：{\"type\":\"MOCK_INTERVIEW_ANSWER_EVALUATION\",\"rawText\":\"不超过三句的具体反馈\",\"normalizedName\":\"1到5的整数分数\",\"rationale\":\"评分依据\"}。基于输入项目快照、追问和用户作答评分；不得把评分当作能力事实，不得编造项目事实，不要生成学习任务。";}
 public List<AiItemPayload> execute(AiJob j,AiProvider p,AiChatClient c){var r=AiItemPayload.parseList(c.complete(p,buildSystemPrompt(),j.getInputSnapshot()));if(r.size()!=1)throw new IllegalStateException("模型未返回作答评分");var x=r.get(0);int score;try{score=Integer.parseInt(x.normalizedName());}catch(Exception ex){throw new IllegalStateException("模型返回的评分无效");}if(score<1||score>5||x.rawText()==null||x.rawText().isBlank()||x.rationale()==null||x.rationale().isBlank())throw new IllegalStateException("模型未返回完整作答评分");return List.of(new AiItemPayload("MOCK_INTERVIEW_ANSWER_EVALUATION",x.rawText().trim(),String.valueOf(score),null,x.rationale().trim(),null,null,null,null));}
}
