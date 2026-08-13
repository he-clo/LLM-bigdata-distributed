package com.atguigu.llmcallback.Repository;

import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.sql.*;

@Repository
public class MovieTaskDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Long createTaskIfNotExists(Long userId, String queryText) {
        try {
            // 尝试插入
            return insertTask(userId, queryText);
        } catch (DataIntegrityViolationException e) {
            // 唯一约束冲突，说明已有活跃任务
            // 查出来返回已有的 taskId
            return findActiveTaskId(userId, queryText);
        }
    }

    private Long findActiveTaskId(Long userId, String queryText) {
        String sql = """
        SELECT id FROM movie_recommend_tasks
        WHERE user_id = ?
          AND query_text = ?
          AND status IN ('PENDING', 'PROCESSING')
        ORDER BY created_at DESC
        LIMIT 1
        """;
        return jdbcTemplate.queryForObject(sql, Long.class, userId, queryText);
    }


    public Long insertTask(Long userId, String queryText) {
        String sql = """
        INSERT INTO movie_recommend_tasks (user_id, query_text, status)
        VALUES (?, ?, ?)
        """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    sql, new String[]{"id"});
            ps.setLong(1, userId);
            ps.setString(2, queryText);
            ps.setObject(3, createPgEnum("PENDING"));  // ← 用 PGobject
            return ps;
        }, keyHolder);

        return keyHolder.getKey().longValue();
    }


    public void updateStatus(Long taskId, String status) {
        String sql = """
        UPDATE movie_recommend_tasks
        SET status = ?, updated_at = NOW()
        WHERE id = ?
        """;

        jdbcTemplate.update(sql, new Object[]{
                createPgEnum(status),  // ← 把 String 转成 PGenum
                taskId
        });
    }

    /**
     * 把 Java String 转成 PostgreSQL ENUM
     */
    private PGobject createPgEnum(String status) {
        try {
            PGobject pgObject = new PGobject();
            pgObject.setType("movie_task_status");  // ← 你的 ENUM 类型名
            pgObject.setValue(status);               // ← 值，比如 'PENDING'
            return pgObject;
        } catch (SQLException e) {
            throw new RuntimeException("转换 ENUM 失败: " + status, e);
        }
    }

}
