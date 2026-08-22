package com.bluedock.file.web.dto;

import java.util.List;

public record UploadInitView(
    boolean done,
    String uploadId,
    long chunkSize,
    int chunkCount,
    List<Integer> received,
    FileView file) {}
