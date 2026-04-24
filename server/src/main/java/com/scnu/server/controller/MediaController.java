package com.scnu.server.controller;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scnu.server.Utils.MinioUtils;
import com.scnu.server.Utils.YtDlpUtils;
import com.scnu.server.entity.MediaFile;
import com.scnu.server.mapper.MediaFileMapper;
import com.scnu.server.service.MediaService;
import org.springframework.web.bind.annotation.GetMapping;


@RestController
@RequestMapping("/media")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class MediaController {
    @Autowired(required = false)
    private MediaFileMapper mediaFileMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MinioUtils minioUtils;

    @Autowired
    private YtDlpUtils ytDlpUtils;

    @Autowired
    private MediaService mediaService;

    @PostMapping("/init-upload")
    public ResponseEntity<String> initUpload() {
        String uploadId = mediaService.initChunkedUpload();
        return ResponseEntity.ok(uploadId);
    }
    
    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) Long userId) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("Upload failed: file is empty");
        }
        if (mediaFileMapper == null) {
            return ResponseEntity.status(500).body("Upload failed: database not ready");
        }
        try {
            String fileUrl = minioUtils.uploadFile(file);

            MediaFile mediaFile = new MediaFile();
            mediaFile.setFilename(file.getOriginalFilename());
            mediaFile.setFilePath(fileUrl);
            mediaFile.setStatus("COMPLETED");
            mediaFile.setUploadTime(LocalDateTime.now());
            if (userId != null) {
                mediaFile.setUserId(userId);
            }

            mediaFileMapper.insert(mediaFile);
            if (userId != null) {
                stringRedisTemplate.delete("media:list:user:" + userId);
            }

            return ResponseEntity.ok("Upload success");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Upload failed: " + e.getMessage());
        }
    }
    
    @PostMapping("/upload-url")
    public ResponseEntity<String> uploadUrl(@RequestParam("url") String url,
            @RequestParam(value = "userId", required = false) Long userId) {
        File tempFile = null;
        try {
            if (url == null || url.isBlank()) {
                return ResponseEntity.badRequest().body("Upload failed: url is empty");
            }
            if (mediaFileMapper == null) {
                return ResponseEntity.status(500).body("Upload failed: database not ready");
            }
            tempFile = ytDlpUtils.downloadVideo(url);
            String fileUrl = minioUtils.uploadLocalFile(tempFile);

            MediaFile mediaFile = new MediaFile();
            mediaFile.setFilename("WEB_" + tempFile.getName());
            mediaFile.setFilePath(fileUrl);
            mediaFile.setStatus("COMPLETED");
            mediaFile.setUploadTime(LocalDateTime.now());

            if (userId != null) {
                mediaFile.setUserId(userId);
            }

            mediaFileMapper.insert(mediaFile);
            if (userId != null) {
                stringRedisTemplate.delete("media:list:user:" + userId);
            }

            return ResponseEntity.ok("Upload success");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Upload Failed: " + e.getMessage());
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
    
    @GetMapping("/list")
    public List<MediaFile> getList(@RequestParam(value = "userId", required = false) Long userId) {
        String cacheKey = "media:list:user:" + (userId == null ? "anon" : userId);

        try {
            String json = stringRedisTemplate.opsForValue().get(cacheKey);
            if (json != null) {
                System.out.println("命中 Redis 缓存，直接返回");
                return objectMapper.readValue(json, new TypeReference<List<MediaFile>>() {});
            }
        } catch (Exception e) {
            System.err.println("Redis read failed: " + e.getMessage());
        }
        QueryWrapper<MediaFile> query = new QueryWrapper<>();
        if (userId != null) {
            query.eq("user_id", userId);
        } else {
            return List.of();
        }
        List<MediaFile> list = mediaFileMapper.selectList(query.orderByDesc("id"));

        try {
            String jsonToWrite = objectMapper.writeValueAsString(list);
            stringRedisTemplate.opsForValue().set(cacheKey, jsonToWrite, 30, TimeUnit.MINUTES);
            System.out.println("已写入 Redis 缓存");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam Long id) throws IOException {
        MediaFile mediaFile = mediaFileMapper.selectById(id);
        if (mediaFile == null) {
            return ResponseEntity.notFound().build();
        }

        String inputPath = mediaFile.getFilePath();
        if (!inputPath.startsWith("http")) {
            if (!new File(inputPath).exists()) {
                return ResponseEntity.notFound().build();
            }
        }

        String outputMp3Path = System.getProperty("java.io.tmpdir") + File.separator + "download_" + UUID.randomUUID()
                + ".mp3";
        boolean success = mediaService.convertToMp3(inputPath, outputMp3Path);
        if (!success) {
            return ResponseEntity.internalServerError().build();
        }

        File mp3File = new File(outputMp3Path);
        Resource resource = new FileSystemResource(mp3File);

        String fileName = "audio.mp3";
        if (mediaFile.getFilename() != null) {
            fileName = mediaFile.getFilename().replaceAll("\\.[^.]+$", "") + ".mp3";
        }
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(resource);
    }
    
    @DeleteMapping("/delete")
    public String delete(@RequestParam("id") Long id,
                         @RequestParam(value = "userId", required = false) Long userId) {
        MediaFile media = mediaFileMapper.selectById(id);
        if (media == null) {
            return "文件不存在";
        }

        if (userId != null && media.getUserId() != null && !media.getUserId().equals(userId)) {
            return "无权删除他人的文件";
        }

        if (media.getFilePath() != null && media.getFilePath().startsWith("http")) {
            minioUtils.removeFile(media.getFilePath());
        }

        mediaFileMapper.deleteById(id);

        if (media.getUserId() != null) {
            String cacheKey = "media:list:user:" + media.getUserId();
            stringRedisTemplate.delete(cacheKey);
        }
        return "删除成功";
    }
}
