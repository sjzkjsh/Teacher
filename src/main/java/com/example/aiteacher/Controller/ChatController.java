package com.example.aiteacher.Controller;

import com.example.aiteacher.Entity.Result;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class ChatController {
    @Autowired
    private ChatClient chatClient;
    @Autowired
    private VectorStore vectorStore;


    @RequestMapping("/chat")
    public Result<String> chat(@RequestParam String question,
                               @RequestParam(defaultValue = "1")String id) {
        String answer = chatClient.prompt()
                .advisors( a->a.param("conversation_id", id))
                .advisors(
                        QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder()
                                .similarityThreshold(0.8)   // 只返回相似度 ≥ 0.8 的文档
                                .topK(4)                    // 返回前 6 个最相似文档
                                .build())
                        .build())
                .user(question)
                .call()
                .content();
        return Result.success(answer);
    }
}
