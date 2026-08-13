package com.atguigu.llmcallback.Service;

import com.atguigu.llmcallback.DTO.Movie;
import com.atguigu.llmcallback.Repository.MovieSearchRepository;
import com.atguigu.llmcallback.context.DataSourceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

//@Component
//public class MovieVectorSearch {
//
//    @Autowired
//    private JdbcTemplate jdbcTemplate;
//
//    @Autowired
//    MovieSearchService movieSearchService;
//
//    /**
//     * 暴露为 MCP 工具。LLM 会根据 description 自动决定是否调用。
//     * @param title 用户输入的查询词
//     * @return Top 10 相似电影列表
//     */
//    @Tool(description = "如果用户要求推荐电影,根据用户描述检索并推荐 Top 10 最相似的电影。返回包含电影名称、年份、简介和相似度分数的列表。")
//    public List<Movie> findTop10Similar(@ToolParam(description = "用户想要查找的电影类型、关键词或描述") String title) {
//        float[] queryVector=movieSearchService.generateMockEmbedding(title);
//        String sql = """
//            SELECT movie_id, title, introduction, genres,
//                   1 - (embedding_text <=> ?::vector) AS similarity
//            FROM movie
//            ORDER BY embedding_text <=> ?::vector
//            LIMIT 10
//        """;
//
//        // 转成 pgvector 字符串
//        String vectorStr = toPgVector(queryVector);
//
//
//        return jdbcTemplate.query(
//                sql,
//                (rs, rowNum) -> new Movie(
//                        rs.getInt("movie_id"),
//                        rs.getString("title"),
//                        rs.getString("introduction"),
//                        rs.getString("genres"),
//                        rs.getDouble("similarity")
//                ),
//                vectorStr, vectorStr
//        );
//    }
//
//    private String toPgVector(float[] v) {
//        StringBuilder sb = new StringBuilder("[");
//        for (int i = 0; i < v.length; i++) {
//            sb.append(v[i]);
//            if (i < v.length - 1) sb.append(",");
//        }
//        return sb.append("]").toString();
//    }
//}


@Service
@Slf4j
public class SimilarMovieSearchService {

    @Autowired
    private MovieSearchRepository repo;

    /**
     * 输入电影标题，返回相似的 10 部电影
     */
    public List<Movie> recommendByTitle(String title) {
        DataSourceContext.set(DataSourceContext.SLAVE);
        try {
            log.info("寻找相似电影, title={}", title);

            Movie sourceMovie = repo.findMovieByTitle(title);
            if (sourceMovie == null || sourceMovie.embeddingText == null) {
                log.warn("未找到电影或向量为空, title={}", title);
                return Collections.emptyList();
            }

            List<Movie> candidates = repo.recallByTextVector(
                    sourceMovie.embeddingText,
                    sourceMovie.movieId
            );

            if (sourceMovie.embeddingImage != null && sourceMovie.embeddingImage.length > 0) {
                candidates = lateFusion(sourceMovie, candidates);
            }

            return candidates.stream()
                    .sorted(Comparator.comparingDouble((Movie m) -> m.similarity).reversed())
                    .limit(10)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("推荐电影失败, title={}", title, e); // ❗不要只 printStackTrace
            return Collections.emptyList(); // ✅ 必须有返回值
        } finally {
            DataSourceContext.clear();
        }
    }

    /**
     * 晚期融合：用源电影的图片向量计算相似度
     */
    private List<Movie> lateFusion(Movie source, List<Movie> candidates) {
        float[] normSourceImage = normalize(source.embeddingImage);

        return candidates.stream().map(candidate -> {
            if (candidate.embeddingImage == null || candidate.embeddingImage.length == 0) {
                // 没有图片向量，只用文本相似度
                return candidate;
            }

            // 计算图片相似度
            float[] normCandidateImage = normalize(candidate.embeddingImage);
            double imageSim = cosineSimilarity(normSourceImage, normCandidateImage);

            // 融合：0.7 * 文本 + 0.3 * 图片
            double finalScore = candidate.similarity * 0.7 + imageSim * 0.3;
            candidate.similarity = finalScore;
            return candidate;
        }).collect(Collectors.toList());
    }

    // --- 工具方法 ---
    private float[] normalize(float[] v) {
        if (v == null) return null;
        double norm = 0;
        for (float x : v) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm < 1e-9) return v;

        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float)(v[i] / norm);
        return out;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null) return 0;
        double dot = 0;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        return dot; // 已归一化，点积即余弦
    }
}