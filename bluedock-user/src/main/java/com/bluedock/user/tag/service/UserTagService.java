package com.bluedock.user.tag.service;

import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.system.service.AdminGuard;
import com.bluedock.user.tag.domain.UserTag;
import com.bluedock.user.tag.repo.UserTagRepository;
import com.bluedock.user.tag.web.dto.UserTagView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserTagService {
  private static final int MAX_TAGS = 100;
  private static final int MAX_NAME = 20;

  private final UserTagRepository tags;
  private final UserAccountRepository users;
  private final AdminGuard adminGuard;

  public UserTagService(
      UserTagRepository tags, UserAccountRepository users, AdminGuard adminGuard) {
    this.tags = tags;
    this.users = users;
    this.adminGuard = adminGuard;
  }

  public Map<String, Object> lists(Long userId) {
    long viewerId = AuthContext.requireUserId();
    long targetId = userId == null || userId <= 0 ? viewerId : userId;
    requireExistingUser(targetId);
    List<UserTagView> list =
        tags.listByUser(targetId).stream()
            .map(
                t ->
                    UserTagView.from(
                        t,
                        tags.countRecognitions(t.getId()),
                        tags.hasRecognition(t.getId(), viewerId)))
            .toList();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("list", list);
    out.put("userId", targetId);
    return out;
  }

  @Transactional
  public UserTagView add(Long userId, String name) {
    long creatorId = AuthContext.requireUserId();
    long targetId = userId == null || userId <= 0 ? creatorId : userId;
    requireTaggableUser(targetId);
    String n = normalizeName(name);
    if (tags.countByUser(targetId) >= MAX_TAGS) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_TAG_LIMIT, MAX_TAGS);
    }
    if (tags.findByUserAndName(targetId, n).isPresent()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_TAG_EXISTS);
    }
    UserTag t = new UserTag();
    t.setId(IdGenerator.nextId());
    t.setUserId(targetId);
    t.setCreatorUserId(creatorId);
    t.setName(n);
    tags.insert(t);
    return UserTagView.from(t, 0L, false);
  }

  @Transactional
  public UserTagView update(Long id, String name) {
    long actorId = AuthContext.requireUserId();
    UserTag t = requireActive(id);
    if (t.getCreatorUserId() != actorId) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, I18nKeys.USER_TAG_DENIED);
    }
    String n = normalizeName(name);
    tags
        .findByUserAndName(t.getUserId(), n)
        .filter(other -> other.getId() != t.getId())
        .ifPresent(
            other -> {
              throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_TAG_EXISTS);
            });
    t.setName(n);
    tags.updateName(t.getId(), n);
    return UserTagView.from(
        t, tags.countRecognitions(t.getId()), tags.hasRecognition(t.getId(), actorId));
  }

  @Transactional
  public Map<String, Object> delete(Long id) {
    long actorId = AuthContext.requireUserId();
    UserTag t = requireActive(id);
    boolean admin = adminGuard.isAdmin(actorId);
    if (t.getCreatorUserId() != actorId && !admin) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, I18nKeys.USER_TAG_DENIED);
    }
    tags.softDelete(t.getId());
    return Map.of("id", t.getId(), "deleted", true);
  }

  @Transactional
  public Map<String, Object> recognize(Long id) {
    long actorId = AuthContext.requireUserId();
    UserTag t = requireActive(id);
    boolean had = tags.hasRecognition(t.getId(), actorId);
    if (had) {
      tags.deleteRecognition(t.getId(), actorId);
    } else {
      tags.insertRecognition(t.getId(), actorId);
    }
    boolean recognized = !had;
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", t.getId());
    out.put("recognized", recognized);
    out.put("recognizeCount", tags.countRecognitions(t.getId()));
    return out;
  }

  private UserTag requireActive(Long id) {
    long tagId = id == null ? 0L : id;
    if (tagId <= 0) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_TAG_NOT_FOUND);
    }
    return tags
        .findActive(tagId)
        .orElseThrow(
            () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_TAG_NOT_FOUND));
  }

  private void requireExistingUser(long userId) {
    if (!users.existsByUserId(userId)) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND);
    }
  }

  private void requireTaggableUser(long userId) {
    UserAccount u =
        users
            .findByUserId(userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCodes.BAD_REQUEST, I18nKeys.USER_TAG_TARGET_INVALID));
    if (u.getIsBot() == 1) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_TAG_TARGET_INVALID);
    }
  }

  private static String normalizeName(String name) {
    String n = name == null ? "" : name.trim();
    if (n.isEmpty() || n.length() > MAX_NAME) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_TAG_NAME_LENGTH);
    }
    return n;
  }
}
