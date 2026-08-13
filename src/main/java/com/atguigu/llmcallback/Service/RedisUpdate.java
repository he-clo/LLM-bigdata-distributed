package com.atguigu.llmcallback.Service;

import com.alibaba.fastjson2.JSON;
import com.atguigu.llmcallback.DTO.Movie;
import com.pgvector.PGvector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RedisUpdate {

    // 建议声明泛型，默认序列化器需配合使用
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private LogFlushService logFlushService;

    public String redisInsert(String userId, String search_title, String top_10, PGvector user_vector) throws Exception {
        String logKey = "user:" + userId + ":log";

        Long version = redisTemplate.opsForHash().increment("user:" + userId, "version", 1);

        Map<String, String> change = new HashMap<>();
        change.put("version", String.valueOf(version));
        change.put("search_movie_title", search_title);
        change.put("recommend_movie_list", top_10);
        change.put("user_vector", user_vector.toString());

        redisTemplate.opsForList().rightPush(logKey, JSON.toJSONString(change));
        logFlushService.tryFlush(userId);
        return "ok";
    }
}
