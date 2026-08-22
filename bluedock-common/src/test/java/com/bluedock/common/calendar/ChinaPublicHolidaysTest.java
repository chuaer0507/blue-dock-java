package com.bluedock.common.calendar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ChinaPublicHolidaysTest {
  @Test
  void covers2026LaborAndNationalDay() {
    assertTrue(ChinaPublicHolidays.isHoliday(LocalDate.of(2026, 5, 1)));
    assertTrue(ChinaPublicHolidays.isHoliday(LocalDate.of(2026, 10, 3)));
    assertFalse(ChinaPublicHolidays.isHoliday(LocalDate.of(2026, 8, 4)));
  }

  @Test
  void covers2025SpringFestival() {
    assertTrue(ChinaPublicHolidays.isHoliday(LocalDate.of(2025, 1, 28)));
    assertTrue(ChinaPublicHolidays.isHoliday(LocalDate.of(2025, 2, 4)));
    assertFalse(ChinaPublicHolidays.isHoliday(LocalDate.of(2025, 2, 5)));
  }

  @Test
  void unknownYearIsNotHoliday() {
    assertFalse(ChinaPublicHolidays.isHoliday(LocalDate.of(2030, 10, 1)));
  }
}
