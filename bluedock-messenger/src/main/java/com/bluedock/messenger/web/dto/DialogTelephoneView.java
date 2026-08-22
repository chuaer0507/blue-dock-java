package com.bluedock.messenger.web.dto;

/** 单聊对方联系电话；{@code add} 为附带写入的 notice 消息（可能为 null）。 */
public record DialogTelephoneView(String telephone, DialogMessageView add) {}
