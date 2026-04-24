package com.scnu.server.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.scnu.server.entity.MediaFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.scnu.server.mapper.MediaFileMapper;

import java.io.IOException;
import java.time.LocalDateTime;

@Service
public class MediaService {
    @Autowired
    private MediaFileMapper mediaFileMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${tool.ffmpeg.dir:}")
    private String ffmpegDir;

    private static final String UPLOAD_DIR = "D:/Project/MediaApp/uploads/";
    private static final String CHUNK_UPLOAD_KEY_PREFIX = "upload:chunked:";

    public MediaService(){
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
    // 记录分片上传会话id并返回
    public String initChunkedUpload(){
        String uploadId = UUID.randomUUID().toString();
        String redisKey = CHUNK_UPLOAD_KEY_PREFIX + uploadId;
        redisTemplate.opsForValue().set(redisKey,"INIT",1,TimeUnit.DAYS);
        return uploadId;
    }

    public String convertVideoToAudio(MultipartFile file) throws IOException, InterruptedException{
        MediaFile mediaFile = new MediaFile();
        mediaFile.setFilename(file.getOriginalFilename());
        mediaFile.setStatus("PROCESSING");
        mediaFile.setUploadTime(LocalDateTime.now());
        mediaFile.setFilePath("");
        mediaFileMapper.insert(mediaFile);

        String fileId = UUID.randomUUID().toString();
        String inputPath = UPLOAD_DIR + fileId + "_input.mp4";
        String outputPath = UPLOAD_DIR + fileId + "_output.mp3";

        File inputFile = new File(inputPath);
        file.transferTo(inputFile);

        boolean success = convertToMp3(inputFile.getAbsolutePath(), new File(outputPath).getAbsolutePath(), null, null);

        if (success) {
            inputFile.delete();
            mediaFile.setStatus("COMPLETED");
            mediaFile.setFilePath(outputPath);
            mediaFileMapper.updateById(mediaFile);
            return outputPath;
        }

        mediaFile.setStatus("FAILED");
        mediaFileMapper.updateById(mediaFile);
        throw new RuntimeException("FFmpeg 转换失败");
    }

    public boolean convertToMp3(String inputPath, String outputPath) {
        return convertToMp3(inputPath, outputPath, null, null);
    }

    public boolean convertToMp3(String inputPath, String outputPath, Integer audioChannels, Integer sampleRate) {
        try {
            List<String> command = new ArrayList<>();
            command.add(resolveFfmpegCommand());
            command.add("-y");
            command.add("-i");
            command.add(inputPath);
            command.add("-vn");
            command.add("-acodec");
            command.add("libmp3lame");
            if (audioChannels != null) {
                command.add("-ac");
                command.add(String.valueOf(audioChannels));
            }
            if (sampleRate != null) {
                command.add("-ar");
                command.add(String.valueOf(sampleRate));
            }
            command.add("-q:a");
            command.add("2");
            command.add(outputPath);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            Process process = pb.start();
            return process.waitFor(15, TimeUnit.MINUTES) && process.exitValue() == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String resolveFfmpegCommand() {
        if (ffmpegDir == null || ffmpegDir.isBlank()) {
            return "ffmpeg";
        }
        File ffmpegExe = new File(ffmpegDir, "ffmpeg.exe");
        if (ffmpegExe.exists()) {
            return ffmpegExe.getAbsolutePath();
        }
        File ffmpeg = new File(ffmpegDir, "ffmpeg");
        if (ffmpeg.exists()) {
            return ffmpeg.getAbsolutePath();
        }
        return "ffmpeg";
    }
}
