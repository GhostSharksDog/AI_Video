package com.scnu.server.strategy;

public interface AiStrategy {
    /**
     * Ai提取文字功能
     * 
     * @param videoPath 视频URL
     */
    String transcribe(String videoPath);

    /**
     * Ai生成总结功能
     * 
     * @param videoPath 视频URL
     */
    String generateSummary(String videoPath);
}
