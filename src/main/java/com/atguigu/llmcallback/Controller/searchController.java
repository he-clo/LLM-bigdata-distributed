package com.atguigu.llmcallback.Controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.atguigu.llmcallback.Advisor.DatabaseSavingAdvisor;
import com.atguigu.llmcallback.DTO.Movie;
import com.atguigu.llmcallback.Enum.TaskStatus;
import com.atguigu.llmcallback.Repository.MovieSearchRepository;
import com.atguigu.llmcallback.Repository.MovieTaskDao;
import com.atguigu.llmcallback.Service.*;
import com.pgvector.PGvector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Year;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/search")
public class searchController {
    @Autowired
    ChatClient chatClient;

    @Autowired
    sendUrl sendUrl;

    @Autowired
    ToolCallService toolCallService;

//    @Autowired
//    DatabaseSavingAdvisor databaseSavingAdvisor;

    @Autowired
    MovieVectorSearch movieVectorSearch;

    @Autowired
    SimilarMovieSearchService similarMovieSearchService;
    
    @Autowired
    RedisUpdate redisUpdate;

    @Autowired
    MovieSearchService movieSearchService;

    @Autowired
    MovieTaskDao    movieTaskDao;

    @GetMapping("/top-10")
    public String top_10(@RequestParam String q,@RequestParam String userid) {
//        String text = chatClient
//                .prompt()
//                .advisors(databaseSavingAdvisor)
//                .messages(new SystemMessage("""
//                        你是一个电影搜索助手。
//                        规则：
//                        1. 获取工具结果后，**必须直接输出工具的原始返回字符串**。
//                        2. **绝对禁止**修改、总结、翻译或添加任何额外文本。
//                        3. 输出必须严格保持格式：ID-Title-Description-URL
//                        """))
//                .messages(new UserMessage(q))
//                .call()
//                .content();
               // log.info(text);   //这里如果不用大模型的话，模糊语义是肯定搜不到的



//        String[] split = text.split("-");
//        for (String a:split){
//            log.warn(a);
//        }
//

       // log.warn(split[2]);
        //List<Movie> top10Similar = movieVectorSearch.findTop10Similar(split[2]);

        Long taskid=null;
        try {
            taskid = movieTaskDao.createTaskIfNotExists(Long.parseLong(userid), q);
            JSONObject text = toolCallService.callAdd(q);
            movieTaskDao.updateStatus(taskid,"PROCESSING");
            int id = text.getIntValue("id");
            String title = text.getString("title");
            String director = text.getString("director");
            List<String> actors = text.getObject("actors", List.class);
            String overview = text.getString("overview");
            String posterUrl = text.getString("poster_url");
            String releaseDate = text.getString("release_date");

            List<Movie> top10Similar = similarMovieSearchService.recommendByTitle(title);
            // 1. 从 releaseDate 字符串中提取前 4 位作为年份


            if (top10Similar.isEmpty() || top10Similar.size() < 3) {
                top10Similar = movieVectorSearch.findTop10Similar(title); //这个通过mcp调用网络工具
            }
            int releaseYear = Integer.parseInt(releaseDate.substring(0, 4));
            if (Year.now().getValue() - releaseYear < 4) {
                sendUrl.test(posterUrl, title);
            }
            //log.warn(top10Similar.toString());
            // 2. 排除指定 ID 的电影
            List<Movie> filtered = top10Similar.stream() //List<Movie>
                    .filter(movie -> !movie.getTitle().equals(title))
                    .collect(Collectors.toList());

            List<Movie> movieByTitle = movieVectorSearch.findTop10Similar(title);
            Movie movie = movieByTitle.get(0);
            String introduction = movie.getIntroduction();
            float[] embeddingText = movieSearchService.generateMockEmbedding(introduction);
            // 5. ✅ 创建“旧向量”（测试用，不查库）
            float[] oldVector = createInitVector();
            // 6. ✅ 新 * 0.3（你明确说只要这个）
            float[] embedding = new float[1024];
            for (int i = 0; i < 1024; i++) {
                embedding[i] = oldVector[i] * 0.7f + embeddingText[i] * 0.3f;
            }
            PGvector pGvector = new PGvector(embedding);//这里不太想写取的逻辑，因为后面还有两个项目要赶，所以只是无限0.3
            String filtered_clean = JSON.toJSONString(filtered);
            try {
                redisUpdate.redisInsert(userid, title, filtered_clean, pGvector);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            movieTaskDao.updateStatus(taskid,"COMPLETED");

            return   chatClient
                    .prompt()
                    .system(s -> s.text("你是一个专业的影评人。请根据提供的电影列表，为用户生成一段推荐语,推荐多部电影。只输出推荐语，不要列出ID。"))
                    .user(u -> u.text("电影列表：{top10Similar}").param("top10Similar", filtered))
                    .call()
                    .content();

        } catch (Exception e) {
            if(taskid != null) {
                log.error("任务执行失败,taskId:{}", taskid, e);
                throw new RuntimeException(e);
            }else{
                log.error("任务创建失败，未获取到taskId:{}",e);
            }
        }
//        return   chatClient
//                .prompt()
//                .system(s -> s.text("你是一个专业的影评人。请根据提供的电影列表，为用户生成一段推荐语,推荐多部电影。只输出推荐语，不要列出ID。"))
//                .user(u -> u.text("电影列表：{top10Similar}").param("top10Similar", filtered))
//                .call()
//                .content();
        return "false";
    }

    private float[] createInitVector() {//创建随机向量，方便测试
        Random random = new Random();
        float[] vector = new float[1024];
        for (int i = 0; i < 1024; i++) {
            // [-0.1, 0.1] 之间的小值，避免数值爆炸
            vector[i] = (random.nextFloat() - 0.5f) * 0.2f;
        }
        return vector;
    }
}
