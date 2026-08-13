package com.atguigu.llmcallback.Configuration;

import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.RequestOriginParser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class CustomOriginParser implements RequestOriginParser {
    @Override
    public String parseOrigin(HttpServletRequest request) {
        // 从请求头中获取来源标识（例如前端或网关传递的 service-name）
        String origin = request.getHeader("X-Service-Origin");
        // 兜底处理，避免返回 null
        return StringUtils.hasText(origin) ? origin : "unknown";
    }
}