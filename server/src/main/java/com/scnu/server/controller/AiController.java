package com.scnu.server.controller;

import java.util.concurrent.TimeUnit;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scnu.server.dto.AnalyzeTaskMsg;
import com.scnu.server.entity.MediaFile;
import com.scnu.server.mapper.MediaFileMapper;
import com.scnu.server.service.AiService;
import com.scnu.server.strategy.AiStrategy;

@RestController
@RequestMapping("/mission")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class AiController {
    @Autowired
    private MediaFileMapper mediaFileMapper;

    @Autowired
    @Qualifier("videoAiStrategy")
    private AiStrategy aiStrategy;

    @Autowired
    private AiService aiService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @GetMapping("/ai")
    public String aiAnalyze(@RequestParam Long id) {
        // 分布式锁：锁住同一个视频，防止重复并发提交
        String lockKey = "lock:analyze:" + id;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(0, -1, TimeUnit.SECONDS)) {
                return "任务提交中，请勿重复点击";
            }

            // 全局限流：1分钟最多10次
            String limitKey = "limit:ai:global";
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(limitKey);
            rateLimiter.trySetRate(RateType.OVERALL, 10, 1, RateIntervalUnit.MINUTES);
            if (!rateLimiter.tryAcquire(1)) {
                return "系统繁忙(限流中),请稍后再试";
            }

            MediaFile file = mediaFileMapper.selectById(id);
            if (file == null) {
                return "文件不存在";
            }
            if (file.getAiSummary() != null && file.getAiSummary().contains("正在")) {
                return "任务已在后台运行，无需重复提交";
            }

            file.setAiSummary("[MQ] 已进入消息队列，等待调度...");
            mediaFileMapper.updateById(file);
            String userIdKey = (file.getUserId() == null) ? "anon" : String.valueOf(file.getUserId());
            stringRedisTemplate.delete("media:list:user:" + userIdKey);

            AnalyzeTaskMsg msg = new AnalyzeTaskMsg(id, "START_ANALYSIS");
            rocketMQTemplate.convertAndSend("video-analysis-topic", msg);

            return "任务已投递到 RocketMQ";
        } catch (Exception e) {
            e.printStackTrace();
            return "提交失败: " + e.getMessage();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @GetMapping("/transcribe")
    public String transcribe(@RequestParam Long id) {
        MediaFile mediaFile = mediaFileMapper.selectById(id);
        if (mediaFile == null) {
            return "找不到文件记录";
        }

        aiService.asyncTranscribe(id);
        return "提取任务已在后台运行，请稍后查看结果";
    }
}
