package com.scnu.server.strategy.impl;

import java.io.File;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.scnu.server.Utils.AsrUtils;
import com.scnu.server.Utils.VisionUtils;
import com.scnu.server.service.MediaService;
import com.scnu.server.strategy.AiStrategy;

@Component("videoAiStrategy")
public class AiStrategyImpl implements AiStrategy {
    @Autowired
    private AsrUtils asrUtils;

    @Autowired
    private VisionUtils visionUtils;

    @Autowired
    private MediaService mediaService;

    @Override
    public String transcribe(String videoPath) {
        return processVideoToText(videoPath);
    }

    @Override
    public String generateSummary(String videoPath) {
        return visionUtils.summarizeVideo(videoPath);
    }

    private String processVideoToText(String inputPath) {
        if (inputPath == null || inputPath.isEmpty()) {
            return "ERROR: input path is empty";
        }

        if (!inputPath.startsWith("http")) {
            File localFile = new File(inputPath);
            if (!localFile.exists()) {
                return "ERROR: local file not found: " + inputPath;
            }
        }

        String tmpDir = System.getProperty("java.io.tmpdir");
        String outputMp3Path = tmpDir + File.separator + "temp_" + UUID.randomUUID() + ".mp3";

        try {
            // ASR转写更适合单声道16k音频
            boolean success = mediaService.convertToMp3(inputPath, outputMp3Path, 1, 16000);
            if (!success) {
                return "FFmpeg 转换失败（可能是网络超时或文件损坏）";
            }
            return asrUtils.audioToText(outputMp3Path);
        } catch (Exception e) {
            e.printStackTrace();
            return "处理异常: " + e.getMessage();
        } finally {
            File mp3 = new File(outputMp3Path);
            if (mp3.exists()) {
                mp3.delete();
            }
        }
    }
}