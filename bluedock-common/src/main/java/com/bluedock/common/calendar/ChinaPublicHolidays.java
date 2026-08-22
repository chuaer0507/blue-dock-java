package com.bluedock.common.calendar;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 中国法定节假日放假日（国务院办公厅公布的放假调休区间）。
 *
 * <p>对齐产品约定：只识别基础节假日放假日，不支持自定义；调休补班日不单独建模（周末仍按周末跳过）。
 * 年份覆盖随国务院通知追加；未知年份返回 false（仅靠周末规则）。
 */
public final class ChinaPublicHolidays {
  private static final Set<LocalDate> HOLIDAYS = build();

  private ChinaPublicHolidays() {}

  /** 是否为法定放假日（含连休中的周末日）。 */
  public static boolean isHoliday(LocalDate day) {
    return day != null && HOLIDAYS.contains(day);
  }

  private static Set<LocalDate> build() {
    Set<LocalDate> set = new HashSet<>();
    // 2025（国办发明电〔2024〕12号）
    addRange(set, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1));
    addRange(set, LocalDate.of(2025, 1, 28), LocalDate.of(2025, 2, 4));
    addRange(set, LocalDate.of(2025, 4, 4), LocalDate.of(2025, 4, 6));
    addRange(set, LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 5));
    addRange(set, LocalDate.of(2025, 5, 31), LocalDate.of(2025, 6, 2));
    addRange(set, LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 8));
    // 2026（国办发明电〔2025〕7号）
    addRange(set, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3));
    addRange(set, LocalDate.of(2026, 2, 15), LocalDate.of(2026, 2, 23));
    addRange(set, LocalDate.of(2026, 4, 4), LocalDate.of(2026, 4, 6));
    addRange(set, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5));
    addRange(set, LocalDate.of(2026, 6, 19), LocalDate.of(2026, 6, 21));
    addRange(set, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 27));
    addRange(set, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7));
    return Collections.unmodifiableSet(set);
  }

  private static void addRange(Set<LocalDate> set, LocalDate from, LocalDate to) {
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      set.add(d);
    }
  }
}
