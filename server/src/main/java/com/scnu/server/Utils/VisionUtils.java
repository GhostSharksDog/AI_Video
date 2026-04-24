package com.scnu.server.Utils;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.utils.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class VisionUtils {

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.qwen.vision-model}")
    private String visionModel;

    @Value("${ai.qwen.video-fps}")
    private Integer defaultVideoFps;

    @Value("${ai.qwen.http-base-url}")
    private String qwenHttpBaseUrl;

    public String summarizeVideo(String videoPathOrUrl) {
        String prompt = "请根据视频内容输出中文摘要: 先给3-6条要点,再给一句总体结论。";
        return summarizeVideo(videoPathOrUrl, prompt);
    }

    // If URL points to localhost, download to temp file first, then send file:// URI.
    public String summarizeVideo(String videoPathOrUrl, String prompt) {
        if (videoPathOrUrl == null || videoPathOrUrl.isBlank()) {
            return "AI request failed: video path/url is empty";
        }
        if (apiKey == null || apiKey.isBlank()) {
            return "AI request failed: api key is empty";
        }

        File tempDownloaded = null;
        try {
            Constants.baseHttpApiUrl = qwenHttpBaseUrl;
            String videoInput = videoPathOrUrl;

            if (isHttpUrl(videoPathOrUrl) && isLocalOnlyUrl(videoPathOrUrl)) {
                tempDownloaded = downloadToTempFile(videoPathOrUrl, ".mp4");
                videoInput = toFileUri(tempDownloaded);
            } else if (!isHttpUrl(videoPathOrUrl)) {
                File localFile = new File(videoPathOrUrl);
                if (!localFile.exists()) {
                    return "AI request failed: local video file not found";
                }
                videoInput = toFileUri(localFile);
            }

            Map<String, Object> videoPart = new HashMap<>();
            videoPart.put("video", videoInput);
            videoPart.put("fps", defaultVideoFps);

            MultiModalMessage userMessage = MultiModalMessage.builder()
                    .role(Role.USER.getValue())
                    .content(Arrays.asList(
                            videoPart,
                            Collections.singletonMap("text", prompt)))
                    .build();

            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .apiKey(apiKey)
                    .model(visionModel)
                    .messages(Arrays.asList(userMessage))
                    .build();

            MultiModalConversation conv = new MultiModalConversation();
            MultiModalConversationResult result = conv.call(param);
            return extractText(result);
        } catch (Exception e) {
            e.printStackTrace();
            return "AI request failed: " + e.getMessage();
        } finally {
            if (tempDownloaded != null && tempDownloaded.exists()) {
                tempDownloaded.delete();
            }
        }
    }
    
    
    private String extractText(MultiModalConversationResult result) {
        try {
            List<Map<String, Object>> content = result.getOutput()
                    .getChoices()
                    .get(0)
                    .getMessage()
                    .getContent();

            if (content == null || content.isEmpty()) {
                return "AI request failed: empty output content";
            }
            Object textObj = content.get(0).get("text");
            if (textObj == null) {
                return "AI request failed: missing text field";
            }

            String text = String.valueOf(textObj);
            return text.isBlank() ? "AI request failed: empty summary" : text;
        } catch (Exception e) {
            return "AI request failed: bad response structure - " + e.getMessage();
        }
    }

    private boolean isHttpUrl(String s) {
        return s.startsWith("http://") || s.startsWith("https://");
    }

    private boolean isLocalOnlyUrl(String s) {
        String lower = s.toLowerCase();
        return lower.contains("localhost") || lower.contains("127.0.0.1") || lower.contains("0.0.0.0");
    }

    // Use HttpClient + URI instead of URL.openStream().
    private File downloadToTempFile(String url, String suffix) throws Exception {
        File temp = new File(System.getProperty("java.io.tmpdir"), "vision_" + UUID.randomUUID() + suffix);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<java.nio.file.Path> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofFile(temp.toPath())
        );

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new RuntimeException("download failed, status=" + status);
        }
        return temp;
    }

    private String toFileUri(File file) {
        return normalizeFileUri(file.toURI().toString());
    }

    private String normalizeFileUri(String uri) {
        if (uri != null && uri.startsWith("file:/") && !uri.startsWith("file://")) {
            return "file:///" + uri.substring("file:/".length());
        }
        return uri;
    }
}
