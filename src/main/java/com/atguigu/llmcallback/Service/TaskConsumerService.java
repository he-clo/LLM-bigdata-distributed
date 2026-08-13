//package com.atguigu.llmcallback.Service;
//
//
//import jakarta.annotation.PostConstruct;
//import jakarta.annotation.PreDestroy;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.ai.chat.client.ChatClient;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.domain.Range;
//import org.springframework.data.redis.connection.RedisZSetCommands;
//import org.springframework.data.redis.connection.stream.*;
//import org.springframework.data.redis.core.RedisCallback;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import java.net.InetAddress;
//import java.time.Duration;
//import java.util.List;
//import java.util.Map;
//import java.util.UUID;
//import java.util.concurrent.Executors;
//import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.TimeUnit;
//
//
//@Component
//@Slf4j
//public class TaskConsumerService {
//
//    private static final String STREAM_KEY = "task:order";
//    private static final String GROUP_NAME = "ai-task-group";
//    private static final String CONSUMER_NAME = getConsumerName();
//    private static final int BLOCK_TIMEOUT_SECONDS = 30;
//    private static final int PENDING_SCAN_INTERVAL_MS = 60000;
//    private static final int MAX_RETRY = 3;
//
//    @Autowired
//    private RedisTemplate<String, Object> redisTemplate;
//    @Autowired
//    private ChatClient chatClient;
//
//    private volatile boolean running = true;
//    private ScheduledExecutorService pendingScanner;
//
//    @PostConstruct
//    public void start() {
//        initGroup();
//        startConsumerThread();
//        startPendingScanner();
//        log.info("消费者 {} 启动成功，监听 Stream: {}", CONSUMER_NAME, STREAM_KEY);
//    }
//
//    /** 1️⃣ 初始化消费组 */
//    private void initGroup() {
//        try {
//            redisTemplate.execute((RedisCallback<Void>) connection -> {
//                connection.streamCommands()
//                        .xGroupCreate(
//                                STREAM_KEY.getBytes(),
//                                GROUP_NAME,
//                                ReadOffset.from("0"),
//                                true
//                        );
//                return null;
//            });
//        } catch (Exception e) {
//            if (!e.getMessage().contains("BUSYGROUP")) {
//                throw e;
//            }
//        }
//    }
//
//    /** 2️⃣ 阻塞消费主线程 */
//    private void startConsumerThread() {
//        Thread consumerThread = new Thread(this::blockingConsume, "redis-stream-consumer");
//        consumerThread.setDaemon(true);
//        consumerThread.start();
//    }
//
//    private void blockingConsume() {
//        while (running) {
//            try {
//                List<MapRecord<String, Object, Object>> records =
//                        redisTemplate.opsForStream()
//                                .read(
//                                        Consumer.from(GROUP_NAME, CONSUMER_NAME),
//                                        StreamReadOptions.empty()
//                                                .count(1)
//                                                .block(Duration.ofSeconds(BLOCK_TIMEOUT_SECONDS)),
//                                        StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
//                                );
//
//                if (records == null || records.isEmpty()) {
//                    continue;
//                }
//
//                records.forEach(this::processRecord);
//
//            } catch (Exception e) {
//                log.error("消费异常，5秒后重试", e);
//                sleep(5000);
//            }
//        }
//    }
//
//    /** 3️⃣ 真正的消息处理 */
//    private void processRecord(MapRecord<String, Object, Object> record) {
//        String taskId = record.getId().getValue();
//        Map<Object, Object> data = record.getValue();
//
//        try {
//            String query = (String) data.get("query");
//            log.info("处理任务 {}: {}", taskId, query);
//
//            String result = chatClient.prompt()
//                    .user(query)
//                    .call()
//                    .content();
//
//            log.info("任务 {} 完成", taskId);
//            redisTemplate.opsForStream().acknowledge(GROUP_NAME, record);
//
//        } catch (Exception e) {
//            log.error("任务 {} 处理失败", taskId, e);
//            handleRetry(taskId, data);
//        }
//    }
//
//    /** 4️⃣ Pending 兜底扫描（关键！） */
//    private void startPendingScanner() {
//        pendingScanner = Executors.newSingleThreadScheduledExecutor();
//        pendingScanner.scheduleAtFixedRate(this::scanPendingMessages,
//                30, PENDING_SCAN_INTERVAL_MS, TimeUnit.MILLISECONDS);
//    }
//
//    private void scanPendingMessages() {
//        try {
//            PendingMessages pendingMessages = redisTemplate.opsForStream()
//                    .pending(
//                            STREAM_KEY,
//                            GROUP_NAME, // 指定消费者
//                            Range.unbounded(),                         // 读取所有范围的消息
//                            100L                                      // ✅ 修正：改为 long 类型 (100L)
//                    );
//
//            // 修正2：增加空值判断，防止空指针
//            if (pendingMessages == null || pendingMessages.isEmpty()) {
//                log.info("未发现未ACK的Pending消息");
//                return;
//            }
//
//            // 修正3：使用正确的 API 获取总数（如果是 Summary）或直接使用列表大小（如果是 Messages）
//            // 因为现在是 PendingMessages，所以直接用 size()
//            long total = pendingMessages.size();
//            log.info("发现 {} 条 Pending 消息", total);
//
//            // 修正4：遍历具体的 PendingMessage 进行处理
//            for (PendingMessage pm : pendingMessages) {
//                // 检查空闲时间是否超过60秒（60000毫秒）
//                if (pm.getElapsedTimeSinceLastDelivery().toMillis() > 60000) {
//                    // 执行重试逻辑，例如：重新读取消息并 ACK，或者使用 claim 方法转移消息
//                    RecordId messageId = pm.getId();
//                    // 这里可以添加你的重试处理逻辑，例如：
//                    // redisTemplate.opsForStream().claim(STREAM_KEY, GROUP_NAME, "new-consumer", 0, messageId);
//                    log.warn("消息 {} 空闲超过60秒，准备重试", messageId);
//                }
//            }
//
//        } catch (Exception e) {
//            log.error("扫描Pending消息失败", e);
//        }
//    }
//
//    /** 5️⃣ 重试 & 死信 */
//    private void handleRetry(String taskId, Map<Object, Object> data) {
//        int retry = Integer.parseInt(data.getOrDefault("retryCount", "0").toString());
//
//        if (retry >= MAX_RETRY) {
//            log.error("任务 {} 超过最大重试次数，进入死信队列", taskId);
//            redisTemplate.opsForList().leftPush("task:dead:letter", data);
//            redisTemplate.opsForStream().acknowledge(GROUP_NAME, STREAM_KEY, taskId);
//            return;
//        }
//
//        data.put("retryCount", retry + 1);
//        log.warn("任务 {} 第 {} 次重试", taskId, retry + 1);
//    }
//
//    /** 6️⃣ 优雅停机 */
//    @PreDestroy
//    public void stop() {
//        log.info("消费者 {} 停止", CONSUMER_NAME);
//        running = false;
//        pendingScanner.shutdownNow();
//    }
//
//    private static String getConsumerName() {
//        try {
//            String ip = InetAddress.getLocalHost().getHostAddress();
//            String port = System.getProperty("server.port", "8080");
//            return ip + ":" + port + ":" + Thread.currentThread().threadId();
//        } catch (Exception e) {
//            return "consumer-" + UUID.randomUUID();
//        }
//    }
//
//    private void sleep(long ms) {
//        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
//    }
//}