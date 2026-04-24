package com.scnu.server.Utils;

import java.io.File;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.utils.Constants;

@Component
public class AsrUtils {
    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.asr.model}")
    private String asrModel;

    @Value("${ai.asr.format}")
    private String asrFormat;

    @Value("${ai.asr.sample-rate}")
    private Integer asrSampleRate;

    @Value("${ai.asr.websocket-url}")
    private String asrWebsocketUrl;

    @Value("${ai.asr.http-base-url}")
    private String asrHttpBaseUrl;

    @Value("${ai.asr.enable-itn}")
    private Boolean enableItn;

    public String audioToText(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return "ASR failed: audio file not found";
        }
        if (apiKey == null || apiKey.isBlank()) {
            return "ASR failed: api key is empty";
        }

        return transcribeByAsr(filePath, asrModel);
    }

    private String transcribeByAsr(String filePath, String model) {
        File file = new File(filePath);
        Recognition recognizer = new Recognition();
        try {
            Constants.baseWebsocketApiUrl = asrWebsocketUrl;

            RecognitionParam param = RecognitionParam.builder()
                    .model(model)
                    .apiKey(apiKey)
                    .format(asrFormat)
                    .sampleRate(asrSampleRate)
                    .build();

            System.out.println("[ASR] Recognition model=" + model + ", format=" + asrFormat + ", sampleRate=" + asrSampleRate);
            String result = recognizer.call(param, file);
            if (result == null || result.isBlank()) {
                return "ASR failed: empty response from Recognition";
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return "ASR failed: " + e.getMessage();
        }
    }

}
