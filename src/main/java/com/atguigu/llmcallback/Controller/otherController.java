package com.atguigu.llmcallback.Controller;

import com.atguigu.llmcallback.DTO.Movie;
import com.atguigu.llmcallback.DTO.MovieOther;
import com.atguigu.llmcallback.DTO.MovieResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

//prompt | model | parse 的解耦


@Slf4j
@RestController
@RequestMapping("/other1")
public class otherController {

    // 关键点1：必须是 final，通过构造函数注入
    private final ChatClient chatClient;

    @Value("classpath:promptTemplate.txt")
    private Resource promptResouce;

    // 关键点2：注入 Builder，在这里构建 Client
    public otherController(ChatClient.Builder chatClientBuilder, ToolCallingManager toolCallingManager, ToolCallbackProvider mcpTools) {
        ToolCallAdvisor build = ToolCallAdvisor.builder().toolCallingManager(toolCallingManager).advisorOrder(Integer.MIN_VALUE+300).build();
        this.chatClient = chatClientBuilder
                .defaultToolCallbacks(mcpTools)
                // 可以在这里固定系统角色，也可以放在 PromptTemplate 里
                .defaultSystem("你是专业的电影推荐师，擅长将结果输出为JSON格式。")
                .defaultAdvisors(build)
                .build();
    }

    @GetMapping("/0")
    public List<MovieResponse> chat(@RequestParam String userInput) {

        //这个是读txt
        PromptTemplate template2 = PromptTemplate.builder()
                .resource(promptResouce)
                .variables(Map.of("question", userInput
                        //,"language", "中文"
                        ))
                .build();

        Prompt prompt1 = template2.create();
        // 1. 定义模板（告诉AI必须返回JSON，这是 entity() 成功的关键）
        String template = """
                请根据用户的问题推荐一部电影。
                问题：{question}
                
                请严格按照以下JSON格式输出，不要包含任何多余的解释或标记（如 ```json）：
                {{
                    "data":{{
                        "movieId": "豆瓣ID",
                        "title": "电影名称",
                        "introduction": "电影简介",
                        "genres": "电影类型"
                    }},
                    "reply": "给用户的一段亲切的自然语言回复，包括推荐原因。例如：为您推荐《XXX》，推荐这部电影的原因是..."
                }}
                """;

        PromptTemplate promptTemplate = new PromptTemplate(template);
        Map<String, Object> variables = new HashMap<>();
        variables.put("question", userInput);

        // 2. 构建 Prompt 对象（注意这里是用 new Prompt(...) 包装 Message）
        Prompt prompt = new Prompt(promptTemplate.createMessage(variables));


        // 3. 调用 entity() 映射为 Java 对象
        return chatClient.prompt(prompt) // 传入 Prompt 对象  //测试prompt1也可
                .call()
                .entity(new ParameterizedTypeReference<List<MovieResponse>>() {}); // 解析为实体
    }
}
