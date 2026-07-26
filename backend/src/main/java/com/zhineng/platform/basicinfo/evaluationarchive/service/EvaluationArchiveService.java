package com.zhineng.platform.basicinfo.evaluationarchive.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhineng.platform.basicinfo.evaluationarchive.dto.EvaluationArchiveDtos;
import com.zhineng.platform.basicinfo.evaluationarchive.repository.EvaluationArchiveRepository;
import com.zhineng.platform.basicinfo.evaluationarchive.storage.EvaluationArchiveStorageService;
import com.zhineng.platform.common.audit.OperationLogRepository;
import com.zhineng.platform.common.user.dto.CurrentUserResponse;
import com.zhineng.platform.common.user.service.CurrentUserService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Year;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EvaluationArchiveService {
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of(
            "ANNUAL_COMPREHENSIVE", "SPECIAL", "AD_HOC");
    private static final Set<String> GRADES = Set.of(
            "EXCELLENT", "GOOD", "QUALIFIED", "UNQUALIFIED", "UNRATED");
    private static final Set<String> ACCESS = Set.of("PUBLIC", "DEPARTMENT", "AUTHORIZED");
    private static final Set<String> CATEGORIES = Set.of(
            "REPORT", "SELF_ASSESSMENT", "RECTIFICATION_LEDGER", "REVIEW_RECORD", "OTHER");
    private static final Set<String> EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "jpg", "jpeg", "png", "zip");
    private static final Set<String> PREVIEWABLE = Set.of("pdf", "jpg", "jpeg", "png");

    private final EvaluationArchiveRepository repository;
    private final EvaluationArchiveStorageService storage;
    private final CurrentUserService currentUser;
    private final OperationLogRepository logs;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;

    public EvaluationArchiveService(
            EvaluationArchiveRepository repository,
            EvaluationArchiveStorageService storage,
            CurrentUserService currentUser,
            OperationLogRepository logs,
            ObjectMapper mapper,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.storage = storage;
        this.currentUser = currentUser;
        this.logs = logs;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public EvaluationArchiveDtos.Page page(
            Long orgId, Integer year, String type, String grade, String status,
            String accessLevel, String keyword, int page, int size
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        validateOptional(type, TYPES, "INVALID_EVALUATION_TYPE", "评估类型");
        validateOptional(grade, GRADES, "INVALID_EVALUATION_GRADE", "评估等级");
        validateOptional(accessLevel, ACCESS, "INVALID_ACCESS_LEVEL", "权限级别");
        if (status != null && !status.isBlank() && !Set.of("DRAFT", "ARCHIVED").contains(status)) {
            bad("INVALID_STATUS", "档案状态无效");
        }
        List<Map<String, Object>> items = repository.page(
                orgId, year, type, grade, status, accessLevel, keyword,
                safeSize, (safePage - 1) * safeSize);
        items.forEach(this::decorate);
        long total = repository.count(orgId, year, type, grade, status, accessLevel, keyword);
        return new EvaluationArchiveDtos.Page(
                items, total, safePage, safeSize, (int) Math.ceil((double) total / safeSize));
    }

    public EvaluationArchiveDtos.Stats stats() {
        Map<String, Object> value = repository.stats();
        return new EvaluationArchiveDtos.Stats(
                number(value.get("total")), number(value.get("drafts")),
                number(value.get("archived")), number(value.get("complete")),
                number(value.get("attachments")));
    }

    public Map<String, Object> detail(long id) {
        Map<String, Object> archive = requireArchive(id);
        decorate(archive);
        archive.put("attachments", repository.attachments(id, false));
        return archive;
    }

    public Map<String, Object> create(EvaluationArchiveDtos.SaveRequest request) {
        validateCreate(request);
        CurrentUserResponse user = currentUser.getCurrentUser();
        try {
            return transactions.execute(status -> {
                validateOrg(request.orgUnitId());
                int sequence = repository.nextArchiveSequence(request.evaluationYear());
                String archiveNo = "DA-%d-%04d".formatted(request.evaluationYear(), sequence);
                long id = repository.insert(
                        archiveNo, request.orgUnitId(), request.evaluationYear(),
                        request.evaluationType(), grade(request.evaluationGrade()),
                        trim(request.description()), access(request.accessLevel()), user.id());
                Map<String, Object> after = detail(id);
                logs.success("M1-6", "EVALUATION_ARCHIVE", id, "CREATE", user.id(),
                        "POST", "/api/basic-info/evaluation-archives", null, json(after));
                return after;
            });
        } catch (DataAccessException exception) {
            if (String.valueOf(exception.getMessage()).contains(
                    "evaluation_archives.org_unit_id")) {
                conflict("DUPLICATE_ARCHIVE", "同一机构、年度和评估类型已存在档案");
                return null;
            }
            throw exception;
        }
    }

    public Map<String, Object> update(long id, EvaluationArchiveDtos.SaveRequest request) {
        requireRequest(request);
        validateOptional(request.evaluationGrade(), GRADES,
                "INVALID_EVALUATION_GRADE", "评估等级");
        validateOptional(request.accessLevel(), ACCESS, "INVALID_ACCESS_LEVEL", "权限级别");
        CurrentUserResponse user = currentUser.getCurrentUser();
        return transactions.execute(status -> {
            Map<String, Object> before = requireDraft(id);
            int rowVersion = requireVersion(request.rowVersion());
            if (repository.update(id, grade(request.evaluationGrade()), trim(request.description()),
                    access(request.accessLevel()), rowVersion, user.id()) != 1) {
                conflict("STALE_ARCHIVE", "档案已变化或不再是草稿，请刷新后重试");
            }
            Map<String, Object> after = detail(id);
            logs.success("M1-6", "EVALUATION_ARCHIVE", id, "UPDATE", user.id(),
                    "PUT", "/api/basic-info/evaluation-archives/" + id,
                    json(before), json(after));
            return after;
        });
    }

    public Map<String, Object> archive(long id, EvaluationArchiveDtos.VersionRequest request) {
        CurrentUserResponse user = currentUser.getCurrentUser();
        return transactions.execute(status -> {
            Map<String, Object> before = requireDraft(id);
            if (!repository.hasActiveReport(id)) {
                conflict("REPORT_REQUIRED", "至少上传一份有效评估报告后才能归档");
            }
            if (repository.archive(id, requireVersion(request == null ? null : request.rowVersion()),
                    user.id()) != 1) {
                conflict("STALE_ARCHIVE", "档案已变化，请刷新后重试");
            }
            Map<String, Object> after = detail(id);
            logs.success("M1-6", "EVALUATION_ARCHIVE", id, "ARCHIVE", user.id(),
                    "POST", "/api/basic-info/evaluation-archives/" + id + "/archive",
                    json(before), json(after));
            return after;
        });
    }

    public Map<String, Object> withdraw(long id, EvaluationArchiveDtos.WithdrawRequest request) {
        if (request == null || trim(request.reason()) == null) {
            bad("WITHDRAW_REASON_REQUIRED", "撤回原因不能为空");
        }
        CurrentUserResponse user = currentUser.getCurrentUser();
        return transactions.execute(status -> {
            Map<String, Object> before = requireArchive(id);
            if (!"ARCHIVED".equals(before.get("status"))) {
                conflict("INVALID_ARCHIVE_STATUS", "仅已归档档案可以撤回");
            }
            if (repository.withdraw(id, requireVersion(request.rowVersion()), user.id()) != 1) {
                conflict("STALE_ARCHIVE", "档案已变化，请刷新后重试");
            }
            Map<String, Object> after = detail(id);
            after.put("withdrawReason", request.reason().trim());
            logs.success("M1-6", "EVALUATION_ARCHIVE", id, "WITHDRAW", user.id(),
                    "POST", "/api/basic-info/evaluation-archives/" + id + "/withdraw",
                    json(before), json(after));
            return after;
        });
    }

    public List<Map<String, Object>> attachments(long archiveId, boolean history) {
        requireArchive(archiveId);
        return repository.attachments(archiveId, history);
    }

    public Map<String, Object> upload(
            long archiveId, String category, String remarks, MultipartFile file
    ) {
        requireDraft(archiveId);
        FileInfo info = validateFile(file);
        validateCategory(category);
        CurrentUserResponse user = currentUser.getCurrentUser();
        EvaluationArchiveStorageService.StoredFile stored = store(file, info.extension);
        try {
            return transactions.execute(status -> {
                requireDraft(archiveId);
                long attachmentId = repository.insertAttachment(
                        archiveId, safeOriginalName(file.getOriginalFilename()),
                        stored.storedName(), stored.relativePath(), trustedContentType(info.extension),
                        info.extension, file.getSize(), sha256(stored.absolutePath()), 1, user.id());
                long relationId = repository.insertAttachmentLink(
                        archiveId, attachmentId, category, UUID.randomUUID().toString(),
                        1, null, trim(remarks), user.id());
                Map<String, Object> after = requireAttachment(relationId);
                logs.success("M1-6", "EVALUATION_ARCHIVE_ATTACHMENT", relationId,
                        "UPLOAD", user.id(), "POST",
                        "/api/basic-info/evaluation-archives/" + archiveId + "/attachments",
                        null, json(after));
                return after;
            });
        } catch (RuntimeException exception) {
            storage.deleteQuietly(stored.relativePath());
            throw exception;
        }
    }

    public Map<String, Object> replace(
            long archiveId, long relationId, String remarks, MultipartFile file
    ) {
        requireDraft(archiveId);
        Map<String, Object> previous = requireAttachment(relationId);
        if (number(previous.get("archive_id")) != archiveId
                || number(previous.get("is_current")) != 1
                || !"ACTIVE".equals(previous.get("attachment_status"))) {
            conflict("ATTACHMENT_NOT_CURRENT", "只能替换当前有效附件");
        }
        FileInfo info = validateFile(file);
        CurrentUserResponse user = currentUser.getCurrentUser();
        EvaluationArchiveStorageService.StoredFile stored = store(file, info.extension);
        try {
            return transactions.execute(status -> {
                requireDraft(archiveId);
                Map<String, Object> current = requireAttachment(relationId);
                if (number(current.get("archive_id")) != archiveId
                        || number(current.get("is_current")) != 1
                        || !"ACTIVE".equals(current.get("attachment_status"))) {
                    conflict("ATTACHMENT_NOT_CURRENT", "只能替换当前有效附件");
                }
                repository.deactivateAttachment(relationId);
                int nextVersion = (int) number(current.get("version_no")) + 1;
                long attachmentId = repository.insertAttachment(
                        archiveId, safeOriginalName(file.getOriginalFilename()),
                        stored.storedName(), stored.relativePath(), trustedContentType(info.extension),
                        info.extension, file.getSize(), sha256(stored.absolutePath()),
                        nextVersion, user.id());
                long newRelationId = repository.insertAttachmentLink(
                        archiveId, attachmentId, String.valueOf(current.get("category")),
                        String.valueOf(current.get("version_group")), nextVersion,
                        relationId, trim(remarks), user.id());
                Map<String, Object> after = requireAttachment(newRelationId);
                logs.success("M1-6", "EVALUATION_ARCHIVE_ATTACHMENT", newRelationId,
                        "REPLACE", user.id(), "POST",
                        "/api/basic-info/evaluation-archives/" + archiveId
                                + "/attachments/" + relationId + "/replace",
                        json(previous), json(after));
                return after;
            });
        } catch (RuntimeException exception) {
            storage.deleteQuietly(stored.relativePath());
            throw exception;
        }
    }

    public void updateAttachmentStatus(
            long archiveId, long relationId, EvaluationArchiveDtos.StatusRequest request
    ) {
        requireDraft(archiveId);
        if (request == null || !"INACTIVE".equals(request.status())) {
            bad("INVALID_ATTACHMENT_STATUS", "附件状态只允许设置为INACTIVE");
        }
        CurrentUserResponse user = currentUser.getCurrentUser();
        transactions.executeWithoutResult(status -> {
            requireDraft(archiveId);
            Map<String, Object> before = requireAttachment(relationId);
            if (number(before.get("archive_id")) != archiveId
                    || number(before.get("is_current")) != 1
                    || !"ACTIVE".equals(before.get("attachment_status"))) {
                conflict("ATTACHMENT_NOT_CURRENT", "只能停用当前有效附件");
            }
            repository.deactivateAttachment(relationId);
            logs.success("M1-6", "EVALUATION_ARCHIVE_ATTACHMENT", relationId,
                    "DEACTIVATE", user.id(), "PUT",
                    "/api/basic-info/evaluation-archives/" + archiveId
                            + "/attachments/" + relationId + "/status",
                    json(before), null);
        });
    }

    public Download download(long relationId, boolean preview) {
        Map<String, Object> attachment = requireAttachment(relationId);
        String extension = String.valueOf(attachment.get("extension")).toLowerCase(Locale.ROOT);
        if (preview && !PREVIEWABLE.contains(extension)) {
            bad("PREVIEW_NOT_SUPPORTED", "该文件类型不支持在线预览，请下载后查看");
        }
        Path path;
        try {
            path = storage.resolveForRead(String.valueOf(attachment.get("storage_path")));
        } catch (IllegalArgumentException exception) {
            bad("INVALID_STORAGE_PATH", "附件存储路径不安全");
            return null;
        } catch (IOException exception) {
            throw new EvaluationArchiveException(
                    "ATTACHMENT_FILE_MISSING", "附件文件不存在", HttpStatus.NOT_FOUND);
        }
        if (!Files.isRegularFile(path)) {
            throw new EvaluationArchiveException(
                    "ATTACHMENT_FILE_MISSING", "附件文件不存在", HttpStatus.NOT_FOUND);
        }
        CurrentUserResponse user = currentUser.getCurrentUser();
        logs.success("M1-6", "EVALUATION_ARCHIVE_ATTACHMENT", relationId,
                preview ? "PREVIEW" : "DOWNLOAD", user.id(), "GET",
                "/api/basic-info/evaluation-archive-attachments/" + relationId
                        + (preview ? "/preview" : "/download"), null, null);
        return new Download(path, String.valueOf(attachment.get("original_name")),
                String.valueOf(attachment.get("content_type")));
    }

    private void validateCreate(EvaluationArchiveDtos.SaveRequest request) {
        requireRequest(request);
        if (request.orgUnitId() == null) bad("ORG_REQUIRED", "请选择机构");
        if (request.evaluationYear() == null
                || request.evaluationYear() < 1900
                || request.evaluationYear() > Year.now().getValue() + 1) {
            bad("INVALID_YEAR", "评估年度无效");
        }
        validateRequired(request.evaluationType(), TYPES,
                "INVALID_EVALUATION_TYPE", "评估类型");
        validateOptional(request.evaluationGrade(), GRADES,
                "INVALID_EVALUATION_GRADE", "评估等级");
        validateOptional(request.accessLevel(), ACCESS, "INVALID_ACCESS_LEVEL", "权限级别");
    }

    private void validateOrg(long orgId) {
        Map<String, Object> org = repository.org(orgId);
        if (org == null) bad("ORG_NOT_FOUND", "机构不存在");
        if (!"ACTIVE".equals(org.get("status"))
                || Set.of("ROOT", "GROUP").contains(String.valueOf(org.get("unit_type")))) {
            conflict("ORG_NOT_AVAILABLE", "仅启用的业务机构可以建立档案");
        }
    }

    private FileInfo validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) bad("EMPTY_FILE", "请选择非空文件");
        if (file.getSize() > MAX_FILE_SIZE) bad("FILE_TOO_LARGE", "单个文件不能超过10MB");
        String name = safeOriginalName(file.getOriginalFilename());
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(extension)) {
            bad("INVALID_FILE_TYPE", "不支持该文件类型");
        }
        validatePreviewSignature(file, extension);
        return new FileInfo(extension);
    }

    private void validatePreviewSignature(MultipartFile file, String extension) {
        if (!PREVIEWABLE.contains(extension)) return;
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(8);
            boolean valid = switch (extension) {
                case "pdf" -> startsWith(header, new byte[]{'%', 'P', 'D', 'F', '-'});
                case "png" -> startsWith(header, new byte[]{
                        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
                case "jpg", "jpeg" -> startsWith(header, new byte[]{
                        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
                default -> false;
            };
            if (!valid) bad("INVALID_FILE_CONTENT", "文件内容与扩展名不匹配");
        } catch (IOException exception) {
            bad("INVALID_FILE_CONTENT", "无法校验文件内容");
        }
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) return false;
        }
        return true;
    }

    private String trustedContentType(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "zip" -> "application/zip";
            default -> "application/octet-stream";
        };
    }

    private EvaluationArchiveStorageService.StoredFile store(
            MultipartFile file, String extension
    ) {
        try (InputStream input = file.getInputStream()) {
            return storage.store(input, extension);
        } catch (IOException exception) {
            throw new EvaluationArchiveException(
                    "FILE_STORAGE_FAILED", "附件保存失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String sha256(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new EvaluationArchiveException(
                    "FILE_HASH_FAILED", "附件校验失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Map<String, Object> requireArchive(long id) {
        Map<String, Object> archive = repository.find(id);
        if (archive == null) {
            throw new EvaluationArchiveException(
                    "ARCHIVE_NOT_FOUND", "档案不存在", HttpStatus.NOT_FOUND);
        }
        return archive;
    }

    private Map<String, Object> requireDraft(long id) {
        Map<String, Object> archive = requireArchive(id);
        if (!"DRAFT".equals(archive.get("status"))) {
            conflict("ARCHIVE_READ_ONLY", "已归档档案只读，请先填写原因撤回");
        }
        return archive;
    }

    private Map<String, Object> requireAttachment(long id) {
        Map<String, Object> attachment = repository.attachment(id);
        if (attachment == null) {
            throw new EvaluationArchiveException(
                    "ATTACHMENT_NOT_FOUND", "附件不存在", HttpStatus.NOT_FOUND);
        }
        return attachment;
    }

    private void decorate(Map<String, Object> archive) {
        long count = number(archive.get("standard_category_count"));
        archive.put("completenessPercent", count * 25);
        archive.put("completenessStatus", count == 4 ? "COMPLETE" : count == 0 ? "EMPTY" : "PARTIAL");
    }

    private void validateCategory(String category) {
        validateRequired(category, CATEGORIES, "INVALID_ATTACHMENT_CATEGORY", "附件分类");
    }

    private void validateRequired(
            String value, Set<String> allowed, String code, String name
    ) {
        if (value == null || !allowed.contains(value)) bad(code, name + "无效");
    }

    private void validateOptional(
            String value, Set<String> allowed, String code, String name
    ) {
        if (value != null && !value.isBlank() && !allowed.contains(value)) bad(code, name + "无效");
    }

    private int requireVersion(Integer version) {
        if (version == null || version < 0) bad("ROW_VERSION_REQUIRED", "缺少有效版本号");
        return version;
    }

    private void requireRequest(Object request) {
        if (request == null) bad("INVALID_REQUEST", "请求内容不能为空");
    }

    private String grade(String value) {
        return value == null || value.isBlank() ? "UNRATED" : value;
    }

    private String access(String value) {
        return value == null || value.isBlank() ? "DEPARTMENT" : value;
    }

    private String trim(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String safeOriginalName(String value) {
        String normalized = value == null ? "" : value.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (name.isBlank() || name.indexOf('\0') >= 0) {
            bad("INVALID_FILE_NAME", "文件名无效");
        }
        return name;
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private String json(Object value) {
        try {
            return value == null ? null : mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("操作日志序列化失败", exception);
        }
    }

    private void bad(String code, String message) {
        throw new EvaluationArchiveException(code, message, HttpStatus.BAD_REQUEST);
    }

    private void conflict(String code, String message) {
        throw new EvaluationArchiveException(code, message, HttpStatus.CONFLICT);
    }

    public record Download(Path path, String fileName, String contentType) {
    }

    private record FileInfo(String extension) {
    }
}
