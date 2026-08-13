package com.atguigu.llmcallback.Service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;


import java.time.Duration;

import java.util.List;

import java.util.Map;

@Service
public class ToolCallService{


    @Value("${mcp.server.url}")
    private String sseServerUrl;

    private McpSyncClient mcpClient;

    @PostConstruct
    public void init() {
        // ✅ 改用 StreamableHttpTransport
        var transport = HttpClientStreamableHttpTransport.builder(sseServerUrl).build();

        mcpClient = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(30))
                .build();
        mcpClient.initialize();
        testAndListTools();
        System.out.println(callAdd("给阿嬷的情书"));
    }

    /**
     * 列出并打印服务端提供的所有工具
     */
    public void testAndListTools() {
        if (mcpClient == null) return;

        // 调用 listTools 获取工具列表
        McpSchema.ListToolsResult toolsResult = mcpClient.listTools();
        List<McpSchema.Tool> tools = toolsResult.tools();

        System.out.println("=== MCP Server 可用工具列表 ===");
        if (tools.isEmpty()) {
            System.out.println("未找到任何工具，请检查服务端(FastMCP)是否启动了对应的 @tool 方法。");
        } else {
            for (McpSchema.Tool tool : tools) {
                System.out.println("工具名称: " + tool.name());
                System.out.println("描述: " + tool.description());
                System.out.println("参数结构: " + tool.inputSchema());
                System.out.println("--------------------------");
            }
        }
    }

    /**
     * 根据名字调用工具的通用方法
     * @param toolName 工具名称 (例如 "add")
     * @param arguments 参数字典
     * @return 调用结果
     */
    public McpSchema.CallToolResult callToolByName(String toolName, Map<String, Object> arguments) {
        if (mcpClient == null) {
            throw new IllegalStateException("MCP Client 未初始化");
        }
        // 构造请求并调用
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(toolName, arguments);
        return mcpClient.callTool(request);
    }

    /**
     * 具体的业务调用方法 (优化版)
     */
    public JSONObject callAdd(String title) {
        McpSchema.CallToolResult result = callToolByName(
                "search_movie",
                Map.of("title", title)
        );

        String jsonText = extractTextFromResult(result);

        // ✅ 关键：解析 JSON
        return JSON.parseObject(jsonText);
    }

    /**

     从 CallToolResult 中提取纯文本内容

     @param result 调用工具返回的 CallToolResult 对象

     @return 拼接后的完整字符串，如果出错或无内容则返回空字符串

     */

    public String extractTextFromResult(McpSchema.CallToolResult result) {
// 判空保护
        if (result == null || result.content() == null || result.content().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (McpSchema.Content content : result.content()) {
// 判断内容类型是否为 TextContent
            if (content instanceof McpSchema.TextContent textContent) {
                sb.append(textContent.text());
            }
        }
        return sb.toString();
    }

    @PreDestroy
    public void destroy() {
        if (mcpClient != null) {
            mcpClient.closeGracefully();
        }
    }

}