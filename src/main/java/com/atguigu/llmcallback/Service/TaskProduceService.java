//package com.atguigu.llmcallback.Service;
//
//import com.atguigu.llmcallback.Exception.APIException;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.redis.connection.stream.RecordId;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.stereotype.Service;
//import com.atguigu.llmcallback.Enum.state;
//import java.util.HashMap;
//import java.util.Map;
//
//@Service
//public class TaskProduceService {
//    @Autowired
//    private RedisTemplate redisTemplate;
//
//    private static final String STREAM_KEY = "task:order";
//
//    public   String putIn(String orderId){
//        Map<String, String> body = new HashMap<>();
//        body.put("orderId", orderId);
//        body.put("retry", "0");
//        body.put("ts", String.valueOf(System.currentTimeMillis()));
//
//        // * 让 Redis 自增 ID
//        try {
//            RecordId recordId = redisTemplate.opsForStream()
//                    .add(STREAM_KEY, body);
//
//            return recordId.getValue();
//        } catch (Exception e) {
//            throw new APIException(state.REDIS_IN_FAIL);
//        }
//
//        //System.out.println("sent: " + recordId);
//
//    }
//}
