package com.atguigu.llmcallback.Service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.atguigu.llmcallback.Configuration.RoutingDataSource;
import com.atguigu.llmcallback.context.DataSourceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LogFlushService {
    @Autowired
    DataSource dataSource;

    @Autowired
    private ObjectMapper objectMapper;

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<List> batchPopLuaScript;

    private static final int THRESHOLD = 3;//这里方便测试，改成3条

    /**
     * 每次写入 Redis 后调用
     */
    public void tryFlush(String userId) throws Exception {
        String logKey = "user:" + userId + ":log";

        // Lua 原子操作：长度 > 300 则返回数据并清空
        List<String> logs = redisTemplate.execute(
                batchPopLuaScript,
                Collections.singletonList(logKey),
                String.valueOf(THRESHOLD)
        );

        if (logs != null && !logs.isEmpty()) {
            // 批量入库（JDBC batch / MyBatis batch / JPA batch）
            DataSourceContext.set(DataSourceContext.MASTER);
            try {
                saveToDb(userId, logs);
            } finally {
                DataSourceContext.clear(); // ✅ 必须清
            }
        }
    }

//        change.put("version", String.valueOf(version));
//        change.put("search_movie_title", search_title);
//        change.put("recommend_movie_list", top_10.toString());
//        change.put("user_vector", user_vector.toString());

  @Transactional(rollbackFor = Exception.class)
    public void saveToDb(String userId, List<String> logs) throws Exception {

        // 1. 修改 SQL：移除了 ON CONFLICT，改为标准的 INSERT
        String sql =
                "INSERT INTO user_movie_profile " +
                        "(user_id, liked_titles, top_10, aggregated_vector, version) " +
                        "VALUES (?, ARRAY[?]::text[], ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // 2. 遍历 logs 列表，将每一条日志都加入批处理
            for (String logStr : logs) {
                JSONObject json = JSON.parseObject(logStr);

                String searchTitle = json.getString("search_movie_title");

                List<String> topTenList = json.getObject("recommend_movie_list", List.class);
                String topTenJson = objectMapper.writeValueAsString(topTenList);

                String vectorStr = json.getString("user_vector");
                Long version = json.getLong("version");

                PGobject pgJsonb = new PGobject();
                pgJsonb.setType("jsonb");
                pgJsonb.setValue(topTenJson);

                PGvector pgVector = new PGvector(vectorStr);

                ps.setLong(1, Long.parseLong(userId));
                ps.setString(2, searchTitle);
                ps.setObject(3, pgJsonb);
                ps.setObject(4, pgVector);
                ps.setLong(5, version);

                // 将当前行加入批处理队列
                ps.addBatch();
            }

            // 3. 一次性执行数据库插入（包含所有行）
            ps.executeBatch();
        }
    }
}