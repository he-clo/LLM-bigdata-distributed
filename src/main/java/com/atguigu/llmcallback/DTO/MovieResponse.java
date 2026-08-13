package com.atguigu.llmcallback.DTO;

import lombok.Data;

@Data
public class MovieResponse {
    // 1. 结构化数据（用于存数据库）
    private MovieOther data;

    // 2. 自然语言回复（用于展示给用户）
    private String reply;
}
