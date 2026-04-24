package com.scnu.server.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("media_files")
public class MediaFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String filename;
    private String status;
    private String filePath;
    private String aiSummary;
    private String transcriptText;
    private String coverUrl;
    private LocalDateTime uploadTime;
}
