package com.scnu.server.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class AnalyzeTaskMsg implements Serializable{
    private Long mediaId;
    private String action;

    public AnalyzeTaskMsg() {
    }

    public AnalyzeTaskMsg(Long mediaId, String action) {
        this.mediaId = mediaId;
        this.action = action;
    }
}
