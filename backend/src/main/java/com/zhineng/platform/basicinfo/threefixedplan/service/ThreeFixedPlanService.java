package com.zhineng.platform.basicinfo.threefixedplan.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhineng.platform.basicinfo.threefixedplan.dto.ThreeFixedDtos;
import com.zhineng.platform.basicinfo.threefixedplan.parser.SimpleDocumentParser;
import com.zhineng.platform.basicinfo.threefixedplan.repository.ThreeFixedPlanRepository;
import com.zhineng.platform.basicinfo.threefixedplan.repository.ThreeFixedPlanRepository.ParseWrite;
import com.zhineng.platform.basicinfo.threefixedplan.storage.ThreeFixedStorageService;
import com.zhineng.platform.common.audit.OperationLogRepository;
import com.zhineng.platform.common.user.dto.CurrentUserResponse;
import com.zhineng.platform.common.user.service.CurrentUserService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ThreeFixedPlanService {
    public static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> FILE_TYPES = Set.of("XLSX", "DOCX", "PDF");
    private static final Set<String> TARGET_FIELDS = Set.of(
            "PLAN_NAME","DOCUMENT_NO","EFFECTIVE_DATE","ORGANIZATION_NAME",
            "ORGANIZATION_NATURE","STAFFING_TYPE","APPROVED_STAFFING",
            "MAIN_RESPONSIBILITIES","INTERNAL_DEPARTMENTS","REMARKS");

    private final ThreeFixedPlanRepository repository;
    private final ThreeFixedStorageService storage;
    private final SimpleDocumentParser parser;
    private final CurrentUserService currentUserService;
    private final OperationLogRepository logs;
    private final ObjectMapper mapper;

    public ThreeFixedPlanService(
            ThreeFixedPlanRepository repository,
            ThreeFixedStorageService storage,
            SimpleDocumentParser parser,
            CurrentUserService currentUserService,
            OperationLogRepository logs,
            ObjectMapper mapper
    ) {
        this.repository = repository;
        this.storage = storage;
        this.parser = parser;
        this.currentUserService = currentUserService;
        this.logs = logs;
        this.mapper = mapper;
    }

    public ThreeFixedDtos.Page page(
            Long orgUnitId, String keyword, String status, String year, int page, int size
    ) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(5, size));
        long total = repository.count(orgUnitId, keyword, status, year);
        return new ThreeFixedDtos.Page(
                repository.list(orgUnitId, keyword, status, year, safeSize,
                        (safePage - 1) * safeSize),
                total, safePage, safeSize, (int) Math.ceil(total / (double) safeSize));
    }

    public Map<String, Object> plan(long id) {
        Map<String, Object> plan = require(repository.plan(id), "PLAN_NOT_FOUND", "未找到三定方案");
        Map<String, Object> result = new LinkedHashMap<>(plan);
        result.put("versions", repository.versions(id));
        return result;
    }

    public List<Map<String, Object>> versions(long planId) {
        require(repository.plan(planId), "PLAN_NOT_FOUND", "未找到三定方案");
        return repository.versions(planId);
    }

    public Map<String, Object> version(long id) {
        Map<String, Object> version = requireVersion(id);
        Map<String, Object> result = new LinkedHashMap<>(version);
        result.put("parseResults", repository.parseResults(id));
        result.put("attachments", repository.attachments(id));
        return result;
    }

    @Transactional
    public Map<String, Object> createManual(ThreeFixedDtos.ManualRequest request) {
        validateOrg(request == null ? null : request.orgUnitId());
        ThreeFixedDtos.Fields fields = validateFields(request.fields());
        CurrentUserResponse user = currentUserService.getCurrentUser();
        long planId = getOrCreatePlan(request.orgUnitId(), user.id());
        ensureNoOpenVersion(planId);
        int no = repository.nextVersionNo(planId);
        long versionId = repository.insertVersion(planId, no, label(no), "MANUAL",
                "NOT_APPLICABLE", fields, null, user.id());
        logs.success("M1-2", "THREE_FIXED_PLAN_VERSION", versionId, "CREATE_MANUAL",
                user.id(), "POST", "/api/basic-info/three-fixed-plans/manual",
                null, json(version(versionId)));
        return version(versionId);
    }

    @Transactional
    public Map<String, Object> upload(
            long orgUnitId, String requestedPlanName, MultipartFile file, String sourceType
    ) {
        validateOrg(orgUnitId);
        FileInfo info = validateFile(file);
        CurrentUserResponse user = currentUserService.getCurrentUser();
        long planId = getOrCreatePlan(orgUnitId, user.id());
        ensureNoOpenVersion(planId);
        ThreeFixedStorageService.StoredFile stored = null;
        try {
            stored = storage.store(file.getInputStream(), info.extension.toLowerCase(Locale.ROOT));
            SimpleDocumentParser.ParsedDocument parsed;
            try {
                parsed = parser.parse(stored.absolutePath(), info.extension,
                        repository.activeMappings(info.extension));
            } catch (Exception parseFailure) {
                parsed = new SimpleDocumentParser.ParsedDocument(
                        "", Map.of(), "FAILED");
            }
            int no = repository.nextVersionNo(planId);
            ThreeFixedDtos.Fields fields = fieldsFromParsed(
                    parsed.fields(), requestedPlanName, baseName(file.getOriginalFilename()));
            long versionId = repository.insertVersion(planId, no, label(no), sourceType,
                    parsed.status(), fields, parsed.text(), user.id());
            repository.replaceParseResults(versionId, parseWrites(parsed));
            repository.insertAttachment(versionId, safeOriginalName(file.getOriginalFilename()),
                    stored.storedName(), stored.relativePath(), file.getContentType(),
                    info.extension.toLowerCase(Locale.ROOT), file.getSize(),
                    sha256(stored.absolutePath()), user.id());
            logs.success("M1-2", "THREE_FIXED_PLAN_VERSION", versionId, "UPLOAD",
                    user.id(), "POST", "/api/basic-info/three-fixed-plans/upload",
                    null, json(version(versionId)));
            return version(versionId);
        } catch (IOException exception) {
            if (stored != null) storage.deleteQuietly(stored.relativePath());
            throw new ThreeFixedException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_ERROR", "文件保存失败");
        } catch (RuntimeException exception) {
            if (stored != null) storage.deleteQuietly(stored.relativePath());
            throw exception;
        }
    }

    @Transactional
    public Map<String, Object> update(long id, ThreeFixedDtos.UpdateRequest request) {
        Map<String, Object> before = requireVersion(id);
        ensureEditable(before);
        int rowVersion = requireVersionNumber(request.rowVersion());
        ThreeFixedDtos.Fields fields = validateFields(request.fields());
        CurrentUserResponse user = currentUserService.getCurrentUser();
        if (repository.updateFields(id, fields, rowVersion, user.id()) != 1) stale();
        repository.updateCorrections(id, correctionMap(fields));
        Map<String, Object> after = version(id);
        logs.success("M1-2", "THREE_FIXED_PLAN_VERSION", id, "UPDATE_FIELDS",
                user.id(), "PUT", "/api/basic-info/three-fixed-plan-versions/" + id,
                json(before), json(after));
        return after;
    }

    @Transactional
    public Map<String, Object> reparse(long id, ThreeFixedDtos.SubmitRequest request) {
        Map<String, Object> before = requireVersion(id);
        ensureEditable(before);
        int rowVersion = requireVersionNumber(request.rowVersion());
        Map<String, Object> attachment = repository.attachmentForVersion(id);
        if (attachment == null) bad("ATTACHMENT_REQUIRED", "手动录入版本没有可重新解析的附件");
        String type = String.valueOf(attachment.get("extension")).toUpperCase(Locale.ROOT);
        try {
            var parsed = parser.parse(storage.resolve(String.valueOf(attachment.get("storage_path"))),
                    type, repository.activeMappings(type));
            ThreeFixedDtos.Fields current = fieldsFromMap(before);
            ThreeFixedDtos.Fields merged = merge(current, parsed.fields());
            CurrentUserResponse user = currentUserService.getCurrentUser();
            if (repository.updateAfterParse(id, merged, parsed.status(), parsed.text(),
                    rowVersion, user.id()) != 1) stale();
            repository.replaceParseResults(id, parseWrites(parsed));
            logs.success("M1-2", "THREE_FIXED_PLAN_VERSION", id, "REPARSE",
                    user.id(), "POST", "/api/basic-info/three-fixed-plan-versions/" + id + "/reparse",
                    json(before), json(version(id)));
            return version(id);
        } catch (IOException exception) {
            throw new ThreeFixedException(HttpStatus.BAD_REQUEST, "PARSE_FAILED", "文件重新解析失败");
        }
    }

    @Transactional
    public Map<String, Object> submit(long id, ThreeFixedDtos.SubmitRequest request) {
        Map<String, Object> before = requireVersion(id);
        CurrentUserResponse user = currentUserService.getCurrentUser();
        if (repository.submit(id, requireVersionNumber(request.rowVersion()), user.id()) != 1) {
            conflict("INVALID_WORKFLOW", "仅退回版本可以重新提交，或版本已变化");
        }
        logs.success("M1-2", "THREE_FIXED_PLAN_VERSION", id, "SUBMIT_REVIEW",
                user.id(), "POST", "/api/basic-info/three-fixed-plan-versions/" + id + "/submit",
                json(before), json(version(id)));
        return version(id);
    }

    @Transactional
    public Map<String, Object> review(long id, ThreeFixedDtos.ReviewRequest request) {
        Map<String, Object> before = requireVersion(id);
        String result = upper(request.result());
        String opinion = trim(request.opinion());
        int rowVersion = requireVersionNumber(request.rowVersion());
        CurrentUserResponse user = currentUserService.getCurrentUser();
        if ("RETURNED".equals(result)) {
            if (opinion == null) bad("OPINION_REQUIRED", "退回时必须填写复核意见");
            if (repository.returnVersion(id, opinion, rowVersion, user.id()) != 1) {
                conflict("INVALID_WORKFLOW", "仅待复核版本可以退回，或版本已变化");
            }
        } else if ("CONFIRMED".equals(result)) {
            if (repository.confirm(id, rowVersion, user.id(), opinion) != 1) {
                conflict("INVALID_WORKFLOW", "仅待复核版本可以确认，或版本已变化");
            }
            repository.publish(number(before.get("plan_id")), id, user.id());
        } else {
            bad("INVALID_REVIEW_RESULT", "复核结果只能是 CONFIRMED 或 RETURNED");
        }
        logs.success("M1-2", "THREE_FIXED_PLAN_VERSION", id, result,
                user.id(), "POST", "/api/basic-info/three-fixed-plan-versions/" + id + "/review",
                json(before), json(version(id)));
        return version(id);
    }

    public List<Map<String, Object>> mappings() {
        return repository.mappings();
    }

    @Transactional
    public Map<String, Object> createMapping(ThreeFixedDtos.MappingRequest request) {
        validateMapping(request);
        CurrentUserResponse user = currentUserService.getCurrentUser();
        long id = repository.createMapping(normalizeMapping(request), user.id());
        logs.success("M1-2", "THREE_FIXED_FIELD_MAPPING", id, "CREATE_MAPPING",
                user.id(), "POST", "/api/basic-info/three-fixed-field-mappings",
                null, json(request));
        return mappingById(id);
    }

    @Transactional
    public Map<String, Object> updateMapping(long id, ThreeFixedDtos.MappingRequest request) {
        validateMapping(request);
        CurrentUserResponse user = currentUserService.getCurrentUser();
        if (repository.updateMapping(id, normalizeMapping(request), user.id()) != 1) notFound("未找到字段映射");
        logs.success("M1-2", "THREE_FIXED_FIELD_MAPPING", id, "UPDATE_MAPPING",
                user.id(), "PUT", "/api/basic-info/three-fixed-field-mappings/" + id,
                null, json(request));
        return mappingById(id);
    }

    @Transactional
    public Map<String, Object> updateMappingStatus(
            long id, ThreeFixedDtos.MappingStatusRequest request
    ) {
        String status = upper(request.status());
        if (!Set.of("ACTIVE", "INACTIVE").contains(status)) bad("INVALID_STATUS", "映射状态无效");
        CurrentUserResponse user = currentUserService.getCurrentUser();
        if (repository.updateMappingStatus(id, status, user.id()) != 1) notFound("未找到字段映射");
        logs.success("M1-2", "THREE_FIXED_FIELD_MAPPING", id, "UPDATE_MAPPING_STATUS",
                user.id(), "PUT", "/api/basic-info/three-fixed-field-mappings/" + id + "/status",
                null, json(request));
        return mappingById(id);
    }

    public Download download(long attachmentId) {
        Map<String, Object> attachment = repository.attachment(attachmentId);
        if (attachment == null) notFound("未找到附件");
        Path path = storage.resolve(String.valueOf(attachment.get("storage_path")));
        if (!Files.isRegularFile(path)) notFound("附件文件不存在");
        return new Download(path, String.valueOf(attachment.get("original_name")),
                (String) attachment.get("content_type"));
    }

    private long getOrCreatePlan(long orgId, long userId) {
        Long id = repository.findPlanIdByOrg(orgId);
        return id == null ? repository.createPlan(orgId, userId) : id;
    }

    private void ensureNoOpenVersion(long planId) {
        if (repository.hasOpenVersion(planId)) {
            conflict("OPEN_VERSION_EXISTS", "该机构已有待处理版本，请先完成复核");
        }
    }

    private void validateOrg(Long id) {
        if (id == null || !repository.activeOrgExists(id)) {
            bad("INVALID_ORG_UNIT", "请选择有效的启用机构");
        }
    }

    private FileInfo validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) bad("FILE_REQUIRED", "请选择文件");
        if (file.getSize() > MAX_FILE_SIZE) bad("FILE_TOO_LARGE", "单文件不能超过10MB");
        String original = safeOriginalName(file.getOriginalFilename());
        int dot = original.lastIndexOf('.');
        String extension = dot < 0 ? "" : original.substring(dot + 1).toUpperCase(Locale.ROOT);
        if (!FILE_TYPES.contains(extension)) {
            bad("UNSUPPORTED_FILE_TYPE", "仅支持 .xlsx、.docx、.pdf");
        }
        return new FileInfo(extension);
    }

    private ThreeFixedDtos.Fields validateFields(ThreeFixedDtos.Fields f) {
        if (f == null || trim(f.planName()) == null) bad("PLAN_NAME_REQUIRED", "方案名称不能为空");
        if (f.approvedStaffing() != null && f.approvedStaffing() < 0) {
            bad("INVALID_STAFFING", "核定编制不能小于0");
        }
        if (trim(f.effectiveDate()) != null) {
            try { LocalDate.parse(f.effectiveDate().trim()); }
            catch (Exception e) { bad("INVALID_DATE", "生效日期必须为YYYY-MM-DD"); }
        }
        return new ThreeFixedDtos.Fields(
                f.planName().trim(), trim(f.documentNo()), trim(f.effectiveDate()),
                trim(f.organizationName()), trim(f.organizationNature()), trim(f.staffingType()),
                f.approvedStaffing(), trim(f.mainResponsibilities()),
                trim(f.internalDepartments()), trim(f.remarks()));
    }

    private void validateMapping(ThreeFixedDtos.MappingRequest r) {
        if (r == null || trim(r.sourceLabel()) == null) bad("SOURCE_LABEL_REQUIRED", "来源标签不能为空");
        if (!Set.of("ALL", "XLSX", "DOCX", "PDF").contains(upper(r.fileType()))) {
            bad("INVALID_FILE_TYPE", "映射文件类型无效");
        }
        if (!TARGET_FIELDS.contains(upper(r.targetField()))) bad("INVALID_TARGET_FIELD", "目标字段无效");
    }

    private ThreeFixedDtos.MappingRequest normalizeMapping(ThreeFixedDtos.MappingRequest r) {
        return new ThreeFixedDtos.MappingRequest(
                upper(r.fileType()), r.sourceLabel().trim(), upper(r.targetField()),
                r.sortOrder() == null ? 0 : r.sortOrder(), r.rowVersion());
    }

    private Map<String, Object> mappingById(long id) {
        return repository.mappings().stream()
                .filter(row -> number(row.get("id")) == id).findFirst()
                .orElseThrow(() -> new ThreeFixedException(
                        HttpStatus.NOT_FOUND, "MAPPING_NOT_FOUND", "未找到字段映射"));
    }

    private ThreeFixedDtos.Fields fieldsFromParsed(
            Map<String, SimpleDocumentParser.ParsedField> parsed,
            String requestedName, String fallbackName
    ) {
        String name = trim(requestedName);
        if (name == null) name = value(parsed, "PLAN_NAME");
        if (name == null) name = fallbackName;
        return new ThreeFixedDtos.Fields(
                name, value(parsed, "DOCUMENT_NO"), value(parsed, "EFFECTIVE_DATE"),
                value(parsed, "ORGANIZATION_NAME"), value(parsed, "ORGANIZATION_NATURE"),
                value(parsed, "STAFFING_TYPE"), integer(value(parsed, "APPROVED_STAFFING")),
                value(parsed, "MAIN_RESPONSIBILITIES"), value(parsed, "INTERNAL_DEPARTMENTS"),
                value(parsed, "REMARKS"));
    }

    private ThreeFixedDtos.Fields merge(
            ThreeFixedDtos.Fields current,
            Map<String, SimpleDocumentParser.ParsedField> parsed
    ) {
        return new ThreeFixedDtos.Fields(
                coalesce(value(parsed, "PLAN_NAME"), current.planName()),
                coalesce(value(parsed, "DOCUMENT_NO"), current.documentNo()),
                coalesce(value(parsed, "EFFECTIVE_DATE"), current.effectiveDate()),
                coalesce(value(parsed, "ORGANIZATION_NAME"), current.organizationName()),
                coalesce(value(parsed, "ORGANIZATION_NATURE"), current.organizationNature()),
                coalesce(value(parsed, "STAFFING_TYPE"), current.staffingType()),
                integerOr(value(parsed, "APPROVED_STAFFING"), current.approvedStaffing()),
                coalesce(value(parsed, "MAIN_RESPONSIBILITIES"), current.mainResponsibilities()),
                coalesce(value(parsed, "INTERNAL_DEPARTMENTS"), current.internalDepartments()),
                coalesce(value(parsed, "REMARKS"), current.remarks()));
    }

    private ThreeFixedDtos.Fields fieldsFromMap(Map<String, Object> row) {
        return new ThreeFixedDtos.Fields(
                string(row, "plan_name"), string(row, "document_no"), string(row, "effective_date"),
                string(row, "organization_name"), string(row, "organization_nature"),
                string(row, "staffing_type"), integerObject(row.get("approved_staffing")),
                string(row, "main_responsibilities"), string(row, "internal_departments"),
                string(row, "remarks"));
    }

    private List<ParseWrite> parseWrites(SimpleDocumentParser.ParsedDocument parsed) {
        return parsed.fields().entrySet().stream().map(entry -> new ParseWrite(
                entry.getKey(), entry.getValue().sourceLabel(), entry.getValue().value(),
                entry.getValue().snippet(), entry.getValue().method(),
                entry.getValue().confidence())).toList();
    }

    private Map<String, String> correctionMap(ThreeFixedDtos.Fields f) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("PLAN_NAME", f.planName()); values.put("DOCUMENT_NO", f.documentNo());
        values.put("EFFECTIVE_DATE", f.effectiveDate()); values.put("ORGANIZATION_NAME", f.organizationName());
        values.put("ORGANIZATION_NATURE", f.organizationNature()); values.put("STAFFING_TYPE", f.staffingType());
        values.put("APPROVED_STAFFING", f.approvedStaffing() == null ? null : String.valueOf(f.approvedStaffing()));
        values.put("MAIN_RESPONSIBILITIES", f.mainResponsibilities());
        values.put("INTERNAL_DEPARTMENTS", f.internalDepartments()); values.put("REMARKS", f.remarks());
        return values;
    }

    private Map<String, Object> requireVersion(long id) {
        return require(repository.version(id), "VERSION_NOT_FOUND", "未找到方案版本");
    }

    private void ensureEditable(Map<String, Object> version) {
        if ("CONFIRMED".equals(String.valueOf(version.get("workflow_status")))) {
            conflict("VERSION_CONFIRMED", "已确认版本不可修改");
        }
    }

    private int requireVersionNumber(Integer version) {
        if (version == null || version < 1) bad("ROW_VERSION_REQUIRED", "缺少有效版本号");
        return version;
    }

    private String label(int no) {
        return "V" + LocalDate.now().getYear() + "." + String.format("%02d", no);
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) >= 0;) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("日志序列化失败", e); }
    }

    private static String safeOriginalName(String name) {
        String safe = name == null ? "未命名文件" : Path.of(name).getFileName().toString();
        return safe.replaceAll("[\\r\\n]", "_");
    }
    private static String baseName(String name) {
        String safe = safeOriginalName(name);
        int dot = safe.lastIndexOf('.');
        return dot > 0 ? safe.substring(0, dot) : safe;
    }
    private static String value(Map<String, SimpleDocumentParser.ParsedField> map, String key) {
        return map.containsKey(key) ? trim(map.get(key).value()) : null;
    }
    private static String coalesce(String first, String second) { return first == null ? second : first; }
    private static Integer integerOr(String value, Integer fallback) {
        Integer parsed = integer(value); return parsed == null ? fallback : parsed;
    }
    private static Integer integer(String value) {
        if (value == null) return null;
        String digits = value.replaceAll("[^0-9-]", "");
        try { return digits.isBlank() ? null : Integer.valueOf(digits); }
        catch (NumberFormatException e) { return null; }
    }
    private static Integer integerObject(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }
    private static String string(Map<String, Object> map, String key) {
        Object value = map.get(key); return value == null ? null : String.valueOf(value);
    }
    private static long number(Object value) { return ((Number) value).longValue(); }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String upper(String value) {
        String trimmed = trim(value); return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }
    private static <T> T require(T value, String code, String message) {
        if (value == null) throw new ThreeFixedException(HttpStatus.NOT_FOUND, code, message);
        return value;
    }
    private static void bad(String code, String message) {
        throw new ThreeFixedException(HttpStatus.BAD_REQUEST, code, message);
    }
    private static void conflict(String code, String message) {
        throw new ThreeFixedException(HttpStatus.CONFLICT, code, message);
    }
    private static void notFound(String message) {
        throw new ThreeFixedException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
    private static void stale() { conflict("STALE_VERSION", "版本已变化，请刷新后重试"); }

    public record Download(Path path, String originalName, String contentType) {
    }
    private record FileInfo(String extension) {
    }
}
