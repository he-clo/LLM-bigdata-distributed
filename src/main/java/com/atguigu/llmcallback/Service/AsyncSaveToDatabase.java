package com.atguigu.llmcallback.Service;

import com.atguigu.llmcallback.DTO.Movie;
import com.atguigu.llmcallback.context.DataSourceContext;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AsyncSaveToDatabase {

    private static final Logger log = LoggerFactory.getLogger(AsyncSaveToDatabase.class);

    @Autowired
    MovieSearchService movieSearchService;

    private  final Set<Integer> hashset=ConcurrentHashMap.newKeySet();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    RabbitTemplate rabbitTemplate;


    @Async("httpExecutor")
    public void splitToList(String result) {
        if (result == null || result.isBlank()) return;

        if (!Character.isDigit(result.charAt(0))) {
            log.warn("⚠️ LLM 未返回标准格式数据 (非数字开头)，跳过。内容片段: {}",
                    result.substring(0,  result.length()));
            return;
        }

        // ✅ 在调用事务方法前设置数据源
        DataSourceContext.set(DataSourceContext.MASTER);
        try {
            log.info("开始存储");
            String[] split = result.split("-");
            if (hashset.add(Integer.parseInt(split[0]))) {
                saveMovie(split); // 此时进入 saveMovie，事务拦截器能正确获取到 MASTER
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // ✅ 任务结束后清理上下文，防止线程池复用污染
            DataSourceContext.clear();
        }
    }


    @Transactional
    public void saveMovie(String[] split ) {
        try {
            float[] floats_1024 = movieSearchService.generateMockEmbedding(split[2]);
            Movie movie = new Movie(new float[512], floats_1024, split[2], split[1]);
            String sql = "INSERT INTO movie (embedding_image, embedding_text, title, introduction) VALUES (?::vector, ?::vector, ?, ?) ON CONFLICT (title) DO NOTHING;";

            int a = jdbcTemplate.update(sql,
                    floatArrayToPgVector(movie.getEmbeddingImage()),
                    floatArrayToPgVector(movie.getEmbeddingText()),
                    movie.getTitle(),
                    movie.getIntroduction()
            );
            if (a == 0) {
                log.info("已存在，未插入");
            } else {
                log.info("✅ JdbcTemplate 插入成功");
            }

            String sql2 = """
                        INSERT INTO film_asset_insert_event (film_id)
                        VALUES (?)
                        """;

            int rows = jdbcTemplate.update(sql2, split[0]);

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rabbitTemplate.convertAndSend("sendUrl",split[3]);
                }
            });
        } finally {
            // 确保在方法结束时清除上下文，防止线程池复用导致的数据源错乱
            DataSourceContext.clear();
        }
    }

    private String floatArrayToPgVector(float[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            sb.append(arr[i]);
            if (i < arr.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
