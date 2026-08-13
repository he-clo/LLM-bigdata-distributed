package com.atguigu.llmcallback.Controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.atguigu.llmcallback.Advisor.DatabaseSavingAdvisor;
//import com.atguigu.llmcallback.Advisor.RedisAdvisor;
//import com.atguigu.llmcallback.Enum.state;
//import com.atguigu.llmcallback.Exception.APIException;
//import com.atguigu.llmcallback.Service.TaskProduceService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ChatModelCallAdvisor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;



import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/ai")
public class ChatController {

    @Autowired
    DatabaseSavingAdvisor databaseSavingAdvisor;

//    @Autowired
//    RedisAdvisor redisAdvisor;

//    @Autowired
//    TaskProduceService taskProduceService;

    private final ChatClient chatClient;

    // ✅ 直接注入全局 Bean，无需手动 new 或 build
    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @PostConstruct
    public void init() {
        log.info("✅ ChatClient 初始化完成。可通过 DEBUG 日志查看已注册的工具列表。");
    }



//    @GetMapping("/chat")
//    public state chat(@RequestParam String q,@RequestParam String orderId) {
//        try {
//            taskProduceService.putIn(orderId);
//            return state.REDIS_IN_SUCC;
//        }catch (APIException e){
//            log.error("Error: " + e.getCode() + " - " + e.getMessage());
//            return state.REDIS_IN_FAIL;
//        }
//    }

    @SentinelResource(value = "api/ai/response",blockHandler = "handleBlock")
    @GetMapping("/response")
    public  String response(@RequestParam String q) {
        return chatClient
                .prompt()
                //.advisors(advisorSpec -> advisorSpec.param("orderId",orderId).advisors(redisAdvisor))
                .advisors(databaseSavingAdvisor)
                .messages(new SystemMessage("""
                        你是一个电影搜索助手。
                        规则：
                        1. 获取工具结果后，**必须直接输出工具的原始返回字符串**。
                        2. **绝对禁止**修改、总结、翻译或添加任何额外文本。
                        3. 输出必须严格保持格式：ID-Title-Description-URL
                        """))
                .messages(new UserMessage(q))
                .call()
                .content();
    }

    public String handleBlock(String q, BlockException ex) {
        return "请求过于频繁，请稍后再试";
    }
}