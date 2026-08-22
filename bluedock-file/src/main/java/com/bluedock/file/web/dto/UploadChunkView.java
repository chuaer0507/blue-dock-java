package com.bluedock.file.web.dto;

import java.util.List;

public record UploadChunkView(String uploadId, List<Integer> received) {}
