package com.bluedock.user.service;

import com.bluedock.auth.crypto.WirePasswordResolver;
import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.license.LicenseCapacity;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.system.service.AdminGuard;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 管理员批量导入：模板 / 预览 / 确认导入。 */
@Service
public class UserImportService {
  public static final int MAX_ROWS = 500;
  private static final Pattern EMAIL =
      Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
  private static final int PASS_MIN = 6;
  private static final int PASS_MAX = 32;
  private static final int EMAIL_MAX = 32;
  private static final String TEMPLATE_CSV =
      "email,nickname,password,profession\n"
          + "alice@example.com,Alice,Pass1234,工程师\n";

  private final UserAccountRepository users;
  private final AdminGuard adminGuard;
  private final WirePasswordResolver passwords;
  private final PasswordEncoder passwordEncoder;
  private final ObjectProvider<LicenseCapacity> licenseCapacity;

  public UserImportService(
      UserAccountRepository users,
      AdminGuard adminGuard,
      WirePasswordResolver passwords,
      PasswordEncoder passwordEncoder,
      ObjectProvider<LicenseCapacity> licenseCapacity) {
    this.users = users;
    this.adminGuard = adminGuard;
    this.passwords = passwords;
    this.passwordEncoder = passwordEncoder;
    this.licenseCapacity = licenseCapacity;
  }

  public byte[] templateCsv() {
    adminGuard.requireAdmin();
    return TEMPLATE_CSV.getBytes(StandardCharsets.UTF_8);
  }

  /** 解析 CSV / xls / xlsx 预览；响应不含 password。 */
  public Map<String, Object> preview(MultipartFile file) {
    adminGuard.requireAdmin();
    if (file == null || file.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_IMPORT_FILE_REQUIRED);
    }
    String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
    List<RawRow> raw;
    if (name.endsWith(".xls") || name.endsWith(".xlsx")) {
      raw = parseExcel(file);
    } else if (name.isEmpty() || name.endsWith(".csv") || name.endsWith(".txt")) {
      raw = parseCsv(file);
    } else {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_IMPORT_FORMAT);
    }
    if (raw.size() > MAX_ROWS) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_IMPORT_LIMIT);
    }
    List<Map<String, Object>> rows = validateRaw(raw, false);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("rows", rows);
    out.put("total", rows.size());
    out.put("okCount", rows.stream().filter(r -> Boolean.TRUE.equals(r.get("ok"))).count());
    return out;
  }

  /**
   * 确认导入。每行须 RSA {@code password}+{@code keyId}（可用顶层 {@code keyId}）；不支持更新已有邮箱。
   */
  @Transactional
  public Map<String, Object> importUsers(List<Map<String, Object>> rows, String keyId) {
    adminGuard.requireAdmin();
    if (rows == null || rows.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_IMPORT_EMPTY);
    }
    if (rows.size() > MAX_ROWS) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_IMPORT_LIMIT);
    }
    List<RawRow> raw = new ArrayList<>();
    int line = 1;
    for (Map<String, Object> r : rows) {
      line++;
      String email = str(r.get("email"));
      String nickname = str(r.get("nickname"));
      String profession = str(r.get("profession"));
      String passwordCipher = str(r.get("password"));
      String rowKeyId = str(r.get("keyId"));
      if (rowKeyId.isBlank()) {
        rowKeyId = keyId == null ? "" : keyId.trim();
      }
      String plain;
      try {
        plain = passwords.requirePlain(rowKeyId, passwordCipher);
      } catch (BusinessException ex) {
        raw.add(new RawRow(line, email, nickname, profession, "", ex.getMessageKey()));
        continue;
      }
      raw.add(new RawRow(line, email, nickname, profession, plain, null));
    }
    List<Map<String, Object>> checked = validateRaw(raw, true);
    int created = 0;
    int failed = 0;
    List<Map<String, Object>> result = new ArrayList<>();
    LicenseCapacity cap = licenseCapacity.getIfAvailable();
    Set<String> createdEmails = new HashSet<>();
    for (Map<String, Object> row : checked) {
      Map<String, Object> item = new LinkedHashMap<>(row);
      if (!Boolean.TRUE.equals(row.get("ok"))) {
        failed++;
        result.add(item);
        continue;
      }
      String email = String.valueOf(row.get("email"));
      if (createdEmails.contains(email) || users.existsByEmail(email)) {
        item.put("ok", false);
        item.put("error", I18nKeys.USER_EMAIL_TAKEN);
        failed++;
        result.add(item);
        continue;
      }
      if (cap != null) {
        try {
          cap.assertCanAddUser();
        } catch (BusinessException ex) {
          item.put("ok", false);
          item.put("error", ex.getMessageKey());
          failed++;
          result.add(item);
          continue;
        }
      }
      String plain = String.valueOf(row.get("_plain"));
      UserAccount u = new UserAccount();
      u.setUserId(IdGenerator.nextId());
      u.setEmail(email);
      u.setNickname(String.valueOf(row.get("nickname")));
      u.setProfession(str(row.get("profession")));
      u.setIdentity("[]");
      u.setPassword(passwordEncoder.encode(plain));
      u.setIsBot(0);
      u.setUserImage("");
      users.insert(u);
      createdEmails.add(email);
      created++;
      item.put("userId", u.getUserId());
      item.put("ok", true);
      item.remove("error");
      result.add(item);
    }
    for (Map<String, Object> item : result) {
      item.remove("_plain");
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("rows", result);
    out.put("created", created);
    out.put("failed", failed);
    return out;
  }

  private List<Map<String, Object>> validateRaw(List<RawRow> raw, boolean keepPlain) {
    Set<String> seen = new HashSet<>();
    List<Map<String, Object>> rows = new ArrayList<>();
    for (RawRow r : raw) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("line", r.line);
      String email = r.email == null ? "" : r.email.trim().toLowerCase(Locale.ROOT);
      String nickname = r.nickname == null ? "" : r.nickname.trim();
      String profession = r.profession == null ? "" : r.profession.trim();
      item.put("email", email);
      item.put("nickname", nickname);
      item.put("profession", profession);
      if (r.decryptError != null) {
        item.put("ok", false);
        item.put("error", r.decryptError);
        rows.add(item);
        continue;
      }
      String err = validateFields(email, nickname, profession, r.password, seen);
      if (err == null && users.existsByEmail(email)) {
        err = I18nKeys.USER_EMAIL_TAKEN;
      }
      if (err != null) {
        item.put("ok", false);
        item.put("error", err);
      } else {
        item.put("ok", true);
        if (keepPlain) {
          item.put("_plain", r.password);
        }
      }
      rows.add(item);
    }
    return rows;
  }

  private static String validateFields(
      String email, String nickname, String profession, String password, Set<String> seen) {
    if (email.isEmpty() || email.length() > EMAIL_MAX || !EMAIL.matcher(email).matches()) {
      return I18nKeys.USER_EMAIL_INVALID;
    }
    if (!seen.add(email)) {
      return I18nKeys.USER_EMAIL_TAKEN;
    }
    if (nickname.length() < 2 || nickname.length() > 20) {
      return I18nKeys.USER_NICKNAME_LENGTH;
    }
    if (password == null || password.length() < PASS_MIN || password.length() > PASS_MAX) {
      return I18nKeys.USER_PASS_LENGTH;
    }
    if (!profession.isEmpty() && (profession.length() < 2 || profession.length() > 20)) {
      return I18nKeys.USER_PROFESSION_LENGTH;
    }
    return null;
  }

  private static List<RawRow> parseCsv(MultipartFile file) {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
      List<RawRow> rows = new ArrayList<>();
      String header = reader.readLine();
      if (header == null) {
        return rows;
      }
      header = stripBom(header).trim().toLowerCase(Locale.ROOT);
      if (!header.contains("email")) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_IMPORT_FORMAT);
      }
      int line = 1;
      String raw;
      while ((raw = reader.readLine()) != null) {
        line++;
        if (raw.isBlank()) {
          continue;
        }
        List<String> cols = splitCsv(raw);
        String email = col(cols, 0);
        String nickname = col(cols, 1);
        String password = col(cols, 2);
        String profession = col(cols, 3);
        rows.add(new RawRow(line, email, nickname, profession, password, null));
        if (rows.size() > MAX_ROWS) {
          break;
        }
      }
      return rows;
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_IMPORT_FORMAT);
    }
  }

  /** 首 sheet；表头须含 email；列名映射 email/nickname/password/profession，缺省按 0..3 位序。 */
  private static List<RawRow> parseExcel(MultipartFile file) {
    DataFormatter formatter = new DataFormatter();
    try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
      Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
      if (sheet == null) {
        return List.of();
      }
      Row headerRow = sheet.getRow(sheet.getFirstRowNum());
      if (headerRow == null) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_IMPORT_FORMAT);
      }
      Map<String, Integer> idx = headerIndex(headerRow, formatter);
      if (!idx.containsKey("email")) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_IMPORT_FORMAT);
      }
      List<RawRow> rows = new ArrayList<>();
      int first = sheet.getFirstRowNum();
      int last = sheet.getLastRowNum();
      for (int r = first + 1; r <= last; r++) {
        Row row = sheet.getRow(r);
        if (row == null || isBlankRow(row, formatter)) {
          continue;
        }
        String email = cell(row, idx.get("email"), formatter);
        String nickname = cell(row, idx.getOrDefault("nickname", 1), formatter);
        String password = cell(row, idx.getOrDefault("password", 2), formatter);
        String profession = cell(row, idx.getOrDefault("profession", 3), formatter);
        rows.add(new RawRow(r + 1, email, nickname, profession, password, null));
        if (rows.size() > MAX_ROWS) {
          break;
        }
      }
      return rows;
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_IMPORT_FORMAT);
    }
  }

  private static Map<String, Integer> headerIndex(Row headerRow, DataFormatter formatter) {
    Map<String, Integer> idx = new HashMap<>();
    short last = headerRow.getLastCellNum();
    for (int c = 0; c < last; c++) {
      String name = cell(headerRow, c, formatter).trim().toLowerCase(Locale.ROOT);
      if (!name.isEmpty()) {
        idx.put(name, c);
      }
    }
    return idx;
  }

  private static boolean isBlankRow(Row row, DataFormatter formatter) {
    short last = row.getLastCellNum();
    if (last < 0) {
      return true;
    }
    for (int c = 0; c < last; c++) {
      if (!cell(row, c, formatter).isBlank()) {
        return false;
      }
    }
    return true;
  }

  private static String cell(Row row, Integer col, DataFormatter formatter) {
    if (row == null || col == null || col < 0) {
      return "";
    }
    Cell cell = row.getCell(col);
    if (cell == null) {
      return "";
    }
    return formatter.formatCellValue(cell).trim();
  }

  private static String stripBom(String s) {
    if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
      return s.substring(1);
    }
    return s;
  }

  private static String col(List<String> cols, int i) {
    return i < cols.size() ? cols.get(i).trim() : "";
  }

  /** 简易 CSV：支持双引号字段。 */
  static List<String> splitCsv(String line) {
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuote = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (inQuote) {
        if (c == '"') {
          if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
            cur.append('"');
            i++;
          } else {
            inQuote = false;
          }
        } else {
          cur.append(c);
        }
      } else if (c == '"') {
        inQuote = true;
      } else if (c == ',') {
        out.add(cur.toString());
        cur.setLength(0);
      } else {
        cur.append(c);
      }
    }
    out.add(cur.toString());
    return out;
  }

  private static String str(Object v) {
    return v == null ? "" : String.valueOf(v).trim();
  }

  private record RawRow(
      int line, String email, String nickname, String profession, String password, String decryptError) {}
}
