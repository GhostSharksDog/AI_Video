package com.scnu.server.consumer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.scnu.server.dto.AnalyzeTaskMsg;
import com.scnu.server.entity.MediaFile;
import com.scnu.server.mapper.MediaFileMapper;
import com.scnu.server.service.AiService;

@Component
@RocketMQMessageListener(topic = "video-analysis-topic", consumerGroup = "video-group")
public class VideoAnalyzeConsumer implements RocketMQListener<AnalyzeTaskMsg>{
     @Autowired
    private AiService aiService;

    @Autowired
    private MediaFileMapper mediaFileMapper;

    @Autowired
    private Executor aiTaskExecutor;

    @Override
    public void onMessage(AnalyzeTaskMsg msg) {
        Long mediaId = msg.getMediaId();
        System.out.println("[MQ] Received analysis task: " + mediaId);

        CompletableFuture.runAsync(() -> {
            try {
                aiService.asyncAnalyze(mediaId);
            } catch (Exception e) {
                System.err.println("[MQ] Task failed: " + e.getMessage());
                markAsFailed(mediaId, e.getMessage());
            }
        }, aiTaskExecutor);
    }

    private void markAsFailed(Long id, String error) {
        MediaFile file = mediaFileMapper.selectById(id);
        if (file != null) {
            file.setAiSummary("分析失败: " + error);
            mediaFileMapper.updateById(file);
        }
    }
}
