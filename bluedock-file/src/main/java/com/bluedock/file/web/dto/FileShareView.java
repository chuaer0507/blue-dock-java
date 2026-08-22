package com.bluedock.file.web.dto;

import java.util.List;

public record FileShareView(long id, int isShared, List<FileShareMemberView> members, FileLinkView link) {}
