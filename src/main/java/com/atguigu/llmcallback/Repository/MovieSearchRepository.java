package com.atguigu.llmcallback.Repository;

import com.atguigu.llmcallback.DTO.Movie;
import com.atguigu.llmcallback.context.DataSourceContext;
import com.pgvector.PGvector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.SQLException;
import java.util.List;


import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.util.Arrays;

@Slf4j
@Repository
public class MovieSearchRepository {

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Step 1：根据标题模糊查找电影，获取其向量
     */
    // ... existing code ...

    @Transactional(readOnly = true)
    public Movie findMovieByTitle(String title) {
        try {
            String sql = """
                SELECT movie_id, title, introduction, genres, 
                       embedding_text, embedding_image
                FROM movie
                WHERE title ILIKE '%' || ? || '%'
                ORDER BY LENGTH(title)
                LIMIT 1
                """;

            List<Movie> movies = jdbc.query(sql, new RowMapper<Movie>() {
                @Override
                public Movie mapRow(ResultSet rs, int rowNum) throws SQLException {
                    Movie m = new Movie();
                    m.movieId = rs.getInt("movie_id");
                    m.title = rs.getString("title");
                    m.introduction = rs.getString("introduction");
                    m.genres = rs.getString("genres");

                    // 修复：vector 类型通常需要按 String 读取后解析，而非 getArray
                    try {
                        String textVecStr = rs.getString("embedding_text");
                        String imageVecStr = rs.getString("embedding_image");

                        m.embeddingText = textVecStr != null ? parseVector(textVecStr) : null;
                        m.embeddingImage = imageVecStr != null ? parseVector(imageVecStr) : null;
                    } catch (Exception e) {
                        log.warn("Failed to parse vector", e);
                        m.embeddingText = null;
                        m.embeddingImage = null;
                    }

                    return m;
                }
            }, title);

            return movies.isEmpty() ? null : movies.get(0);
        } finally {
            // 确保清理上下文
            DataSourceContext.clear();
        }
    }

    /**
     * Step 2：根据向量推荐电影，获取其自身
     */
    @Transactional(readOnly = true)
    public List<Movie> recallByTextVector(float[] queryVec, int excludeMovieId) {
        try {
            String sql = """
                SELECT movie_id, title, introduction, genres,
                       1 - (embedding_text <=> ?::vector) AS similarity
                FROM movie
                WHERE movie_id != ?
                ORDER BY embedding_text <=> ?::vector
                LIMIT 20
                """;

            PGvector pgVector = new PGvector(queryVec);

            return jdbc.query(sql, new RowMapper<Movie>() {
                @Override
                public Movie mapRow(ResultSet rs, int rowNum) throws SQLException {
                    Movie m = new Movie();
                    m.movieId = rs.getInt("movie_id");
                    m.title = rs.getString("title");
                    m.introduction = rs.getString("introduction");
                    m.genres = rs.getString("genres");
                    m.similarity = rs.getDouble("similarity");
                    return m;
                }
            }, pgVector, excludeMovieId, pgVector);
        } finally {
            DataSourceContext.clear();
        }
    }

    // 辅助方法：解析 "[1.0, 2.0]" 格式的字符串为 float[]
    private float[] parseVector(String vecStr) {
        if (vecStr == null || vecStr.isEmpty()) return null;
        // 去掉首尾的 '[' 和 ']'
        String content = vecStr.substring(1, vecStr.length() - 1);
        if (content.isEmpty()) return new float[0];

        String[] parts = content.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }
}