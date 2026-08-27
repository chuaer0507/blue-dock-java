package com.bluedock.project.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.project.domain.Project;
import com.bluedock.project.domain.ProjectFlow;
import com.bluedock.project.domain.ProjectFlowItem;
import com.bluedock.project.repo.ProjectFlowRepository;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.project.web.dto.ProjectFlowView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectFlowService {
  private static final int MAX_ITEMS = 10;
  private static final ObjectMapper JSON = new ObjectMapper();

  private static final String[][] DEFAULT_ITEMS = {
    {"待处理", "start", "#909399"},
    {"进行中", "progress", "#409EFF"},
    {"待测试", "test", "#E6A23C"},
    {"已完成", "end", "#67C23A"},
    {"已取消", "end", "#F56C6C"},
  };

  /** 默认 turns：按 sort 下标 → 目标 sort 列表。 */
  private static final int[][] DEFAULT_TURN_SORTS = {
    {1}, {2, 3}, {1, 3}, {}, {},
  };

  private final ProjectFlowRepository flows;
  private final ProjectRepository projects;
  private final ProjectAccessService access;

  public ProjectFlowService(
      ProjectFlowRepository flows, ProjectRepository projects, ProjectAccessService access) {
    this.flows = flows;
    this.projects = projects;
    this.access = access;
  }

  public List<ProjectFlowView> list(long projectId) {
    long userId = AuthContext.requireUserId();
    access.requireMember(projectId, userId);
    requireActiveProject(projectId);
    return flows.listFlowsByProject(projectId).stream()
        .map(f -> ProjectFlowView.from(f, flows.listItemsByFlow(f.getId())))
        .toList();
  }

  /**
   * 保存工作流（新建或全量更新节点）。契约：{@code POST /api/project/flow/save}。
   *
   * <p>{@code items} 为 JSON 数组；元素字段：{@code id?} · {@code name} · {@code status} · {@code
   * color?} · {@code sort?} · {@code turns?}（目标节点 id 或本批 sort）· {@code userIds?} · {@code
   * usertype?} · {@code columnId?}。空 items 时套用默认 5 节点。
   */
  @Transactional
  public ProjectFlowView save(long projectId, Long flowId, String name, Object itemsRaw) {
    long userId = AuthContext.requireUserId();
    access.requireManage(projectId, userId);
    Project project = requireActiveProject(projectId);
    if (project.getIsPersonal() == 1) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_FLOW_PERSONAL);
    }

    String n = name == null ? "" : name.trim();
    if (n.isEmpty()) {
      n = "默认流程";
    }
    if (n.length() > 100) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_FLOW_NAME_LENGTH);
    }

    List<Map<String, Object>> itemMaps = parseItems(itemsRaw);
    if (itemMaps.isEmpty()) {
      itemMaps = defaultItemMaps();
    }
    if (itemMaps.size() > MAX_ITEMS) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_FLOW_ITEMS_LIMIT, MAX_ITEMS);
    }

    ProjectFlow flow;
    if (flowId != null && flowId > 0) {
      flow =
          flows
              .findActiveFlow(flowId)
              .orElseThrow(
                  () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_FLOW_NOT_FOUND));
      if (flow.getProjectId() != projectId) {
        throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_FLOW_NOT_FOUND);
      }
      flow.setName(n);
      flows.updateFlow(flow);
    } else {
      flow = new ProjectFlow();
      flow.setId(IdGenerator.nextId());
      flow.setProjectId(projectId);
      flow.setName(n);
      flows.insertFlow(flow);
    }

    List<ProjectFlowItem> prepared = new ArrayList<>();
    Map<Integer, Long> sortToId = new HashMap<>();
    Set<Long> keepIds = new HashSet<>();
    int index = 0;
    for (Map<String, Object> raw : itemMaps) {
      long id = asLong(raw.get("id"));
      if (id <= 0) {
        id = IdGenerator.nextId();
      }
      String itemName = asString(raw.get("name")).trim();
      if (itemName.isEmpty() || itemName.length() > 100) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_FLOW_ITEM_NAME);
      }
      String status = asString(raw.get("status")).trim();
      if (status.isEmpty()) {
        status = "progress";
      }
      int sort = raw.containsKey("sort") ? (int) asLong(raw.get("sort")) : index;
      ProjectFlowItem it = new ProjectFlowItem();
      it.setId(id);
      it.setFlowId(flow.getId());
      it.setProjectId(projectId);
      it.setName(itemName);
      it.setStatus(status);
      it.setColor(asString(raw.get("color")));
      it.setSort(sort);
      it.setUserIds(joinIds(raw.get("userIds")));
      it.setUsertype(asString(raw.get("usertype")));
      it.setColumnId(asLong(first(raw, "columnId", "column_id")));
      it.setTurns(""); // filled below
      prepared.add(it);
      sortToId.put(sort, id);
      keepIds.add(id);
      index++;
    }

    // turns: prefer explicit ids; else treat numbers as sort keys in this batch
    for (int i = 0; i < prepared.size(); i++) {
      Object turnsRaw = itemMaps.get(i).get("turns");
      prepared.get(i).setTurns(resolveTurns(turnsRaw, sortToId, keepIds));
    }

    Set<Long> existing =
        flows.listItemsByFlow(flow.getId()).stream()
            .map(ProjectFlowItem::getId)
            .collect(Collectors.toSet());
    for (ProjectFlowItem it : prepared) {
      if (existing.contains(it.getId())) {
        flows.updateItem(it);
      } else {
        flows.insertItem(it);
      }
    }
    flows.softDeleteItemsNotIn(flow.getId(), keepIds);
    return ProjectFlowView.from(flow, flows.listItemsByFlow(flow.getId()));
  }

  @Transactional
  public void delete(long flowId) {
    long userId = AuthContext.requireUserId();
    ProjectFlow flow =
        flows
            .findActiveFlow(flowId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_FLOW_NOT_FOUND));
    access.requireManage(flow.getProjectId(), userId);
    flows.softDeleteFlow(flowId);
  }

  public ProjectFlowItem requireItemInProject(long flowItemId, long projectId) {
    ProjectFlowItem it =
        flows
            .findActiveItem(flowItemId)
            .orElseThrow(
                () ->
                    new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_FLOW_ITEM_NOT_FOUND));
    if (it.getProjectId() != projectId) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_FLOW_ITEM_NOT_FOUND);
    }
    return it;
  }

  private Project requireActiveProject(long projectId) {
    return projects
        .findActive(projectId)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_NOT_FOUND));
  }

  private static List<Map<String, Object>> defaultItemMaps() {
    List<Map<String, Object>> out = new ArrayList<>();
    for (int i = 0; i < DEFAULT_ITEMS.length; i++) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("name", DEFAULT_ITEMS[i][0]);
      m.put("status", DEFAULT_ITEMS[i][1]);
      m.put("color", DEFAULT_ITEMS[i][2]);
      m.put("sort", i);
      List<Integer> turns = new ArrayList<>();
      for (int t : DEFAULT_TURN_SORTS[i]) {
        turns.add(t);
      }
      m.put("turns", turns);
      out.add(m);
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> parseItems(Object raw) {
    if (raw == null) {
      return List.of();
    }
    if (raw instanceof String s) {
      if (s.isBlank()) {
        return List.of();
      }
      try {
        return JSON.readValue(s, new TypeReference<>() {});
      } catch (Exception e) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_FLOW_ITEMS_INVALID);
      }
    }
    if (raw instanceof List<?> list) {
      List<Map<String, Object>> out = new ArrayList<>();
      for (Object o : list) {
        if (o instanceof Map<?, ?> m) {
          out.add((Map<String, Object>) m);
        }
      }
      return out;
    }
    throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_FLOW_ITEMS_INVALID);
  }

  private static String resolveTurns(Object turnsRaw, Map<Integer, Long> sortToId, Set<Long> keepIds) {
    if (turnsRaw == null) {
      return "";
    }
    List<Long> ids = new ArrayList<>();
    if (turnsRaw instanceof List<?> list) {
      for (Object o : list) {
        long v = asLong(o);
        if (v <= 0) {
          continue;
        }
        if (keepIds.contains(v)) {
          ids.add(v);
        } else if (sortToId.containsKey((int) v)) {
          ids.add(sortToId.get((int) v));
        }
      }
    } else if (turnsRaw instanceof String s) {
      for (String part : s.split("[,|]")) {
        String t = part.trim();
        if (t.isEmpty()) {
          continue;
        }
        try {
          long v = Long.parseLong(t);
          if (keepIds.contains(v)) {
            ids.add(v);
          } else if (v <= Integer.MAX_VALUE && sortToId.containsKey((int) v)) {
            ids.add(sortToId.get((int) v));
          }
        } catch (NumberFormatException ignored) {
          // skip
        }
      }
    }
    return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
  }

  private static String joinIds(Object raw) {
    if (raw == null) {
      return "";
    }
    if (raw instanceof List<?> list) {
      return list.stream()
          .map(ProjectFlowService::asLong)
          .filter(id -> id > 0)
          .map(String::valueOf)
          .collect(Collectors.joining(","));
    }
    return asString(raw).trim();
  }

  private static Object first(Map<String, Object> m, String a, String b) {
    if (m.containsKey(a)) {
      return m.get(a);
    }
    return m.get(b);
  }

  private static long asLong(Object o) {
    if (o == null) {
      return 0L;
    }
    if (o instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(o).trim());
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  private static String asString(Object o) {
    return o == null ? "" : String.valueOf(o);
  }
}
