package com.scnu.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.scnu.server.entity.MediaFile;
import com.scnu.server.mapper.MediaFileMapper;
import com.scnu.server.strategy.AiStrategy;

@Service
public class AiService {
    @Autowired
    private MediaFileMapper mediaFileMapper;

    @Autowired
    @Qualifier("videoAiStrategy")
    private AiStrategy aiStrategy;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public void asyncAnalyze(Long mediaId) {
        System.out.println(" [线程池] 开始处理任务,ID: " + mediaId);
        MediaFile mediaFile = mediaFileMapper.selectById(mediaId);
        if (mediaFile == null) {
            return;
        }

        try {
            // 1. 语音转文字
            String text = aiStrategy.transcribe(mediaFile.getFilePath());
            mediaFile.setTranscriptText(text);

            // 2. 智能总结
            String summary = aiStrategy.generateSummary(mediaFile.getFilePath());
            mediaFile.setAiSummary(summary);
            // 3. 保存数据库
            mediaFileMapper.updateById(mediaFile);

            String userIdStr = (mediaFile.getUserId() == null) ? "anon" : String.valueOf(mediaFile.getUserId());
            String cacheKey = "media:list:user:" + userIdStr;
            redisTemplate.delete(cacheKey);

            System.out.println("[AI] Analyze complete, cache cleared for user: " + userIdStr);
        } catch (Exception e) {
            e.printStackTrace();
            String userIdStr = (mediaFile.getUserId() == null) ? "anon" : String.valueOf(mediaFile.getUserId());
            String cacheKey = "media:list:user:" + userIdStr;
            redisTemplate.delete(cacheKey);
        }
    }

    @Async("aiTaskExecutor")
    public void asyncTranscribe(Long mediaId) {
        MediaFile mediaFile = mediaFileMapper.selectById(mediaId);
        if (mediaFile == null) {
            return;
        }

        try {
            String text = aiStrategy.transcribe(mediaFile.getFilePath());
            mediaFile.setTranscriptText(text);
            mediaFileMapper.updateById(mediaFile);

            String userIdStr = (mediaFile.getUserId() == null) ? "anon" : String.valueOf(mediaFile.getUserId());
            redisTemplate.delete("media:list:user:" + userIdStr);
            System.out.println("[AI] Transcribe complete, cache cleared for user: " + userIdStr);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
