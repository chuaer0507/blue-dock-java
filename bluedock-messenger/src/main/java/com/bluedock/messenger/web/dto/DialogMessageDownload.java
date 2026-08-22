package com.bluedock.messenger.web.dto;

import java.io.InputStream;

/**
 * 消息附件下载结果。
 *
 * <p>{@code preview=true} 时仅填 {@code url}/{@code name}/{@code fileId}；否则提供可关闭的 {@code
 * content} 流。
 */
public record DialogMessageDownload(
    boolean preview, long fileId, String name, String url, long size, InputStream content) {}
