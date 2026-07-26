package com.zhineng.platform.basicinfo.staffing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhineng.platform.basicinfo.staffing.dto.StaffingDtos;
import com.zhineng.platform.basicinfo.staffing.excel.StaffingExcelService;
import com.zhineng.platform.basicinfo.staffing.excel.StaffingExcelService.ImportRow;
import com.zhineng.platform.basicinfo.staffing.repository.StaffingRepository;
import com.zhineng.platform.basicinfo.staffing.repository.StaffingRepository.ChangeWrite;
import com.zhineng.platform.basicinfo.staffing.repository.StaffingRepository.LedgerWrite;
import com.zhineng.platform.basicinfo.staffing.repository.StaffingRepository.UnitRow;
import com.zhineng.platform.common.audit.OperationLogRepository;
import com.zhineng.platform.common.user.dto.CurrentUserResponse;
import com.zhineng.platform.common.user.service.CurrentUserService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StaffingService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_BATCH_SIZE = 50;

    private final StaffingRepository repository;
    private final StaffingExcelService excel;
    private final CurrentUserService currentUserService;
    private final OperationLogRepository operationLogs;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;

    public StaffingService(
            StaffingRepository repository,
            StaffingExcelService excel,
            CurrentUserService currentUserService,
            OperationLogRepository operationLogs,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.excel = excel;
        this.currentUserService = currentUserService;
        this.operationLogs = operationLogs;
        this.objectMapper = objectMapper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public StaffingDtos.Page page(
            String keyword, String maintenanceStatus, String anomalyStatus, int page, int size
    ) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(5, size));
        var result = repository.findPage(
                keyword, maintenanceStatus, anomalyStatus, safePage, safeSize);
        return new StaffingDtos.Page(
                result.items(), result.total(), safePage, safeSize,
                (int) Math.ceil(result.total() / (double) safeSize));
    }

    public StaffingDtos.Stats stats(
            String keyword, String maintenanceStatus, String anomalyStatus
    ) {
        return repository.stats(keyword, maintenanceStatus, anomalyStatus);
    }

    public StaffingDtos.ListItem detail(long id) {
        return requireLedger(id);
    }

    public StaffingDtos.ChangePage changes(long id, int page, int size) {
        requireLedger(id);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(5, size));
        var rows = repository.findChanges(id, safePage, safeSize);
        return new StaffingDtos.ChangePage(
                rows.items(), rows.total(), safePage, safeSize,
                (int) Math.ceil(rows.total() / (double) safeSize));
    }

    @Transactional
    public StaffingDtos.ListItem create(StaffingDtos.SaveRequest request) {
        Normalized input = normalize(request, true);
        UnitRow unit = requireMaintainableUnit(input.orgUnitId);
        if (repository.ledgerExistsForUnit(unit.id())) {
            conflict("LEDGER_EXISTS", "该机构已经建立编制人员台账");
        }
        CurrentUserResponse user = currentUserService.getCurrentUser();
        long id = repository.insertLedger(input.write(), user.id());
        if (!Objects.equals(unit.approvedStaffing(), input.approvedStaffing)) {
            repository.updateApprovedStaffing(unit.id(), input.approvedStaffing, user.id());
        }
        StaffingDtos.ListItem after = requireLedger(id);
        insertChange(id, unit.id(), "MANUAL_CREATE", UUID.randomUUID().toString(),
                null, after, input.changeReason, input.dataDate, user.id());
        log(id, "CREATE", "POST", "/api/basic-info/staffing-ledgers", null, after, user.id());
        return after;
    }

    @Transactional
    public StaffingDtos.ListItem update(long id, StaffingDtos.SaveRequest request) {
        StaffingDtos.ListItem before = requireLedger(id);
        Normalized input = normalize(request, false);
        requireVersion(input.versionNo);
        UnitRow unit = requireMaintainableUnit(before.orgUnitId());
        CurrentUserResponse user = currentUserService.getCurrentUser();
        if (repository.updateLedger(id, input.write(before.orgUnitId()), user.id(), input.versionNo) != 1) {
            conflict("STALE_VERSION", "台账已被其他操作修改，请刷新后重试");
        }
        if (!Objects.equals(unit.approvedStaffing(), input.approvedStaffing)) {
            repository.updateApprovedStaffing(unit.id(), input.approvedStaffing, user.id());
        }
        StaffingDtos.ListItem after = requireLedger(id);
        insertChange(id, unit.id(), "MANUAL_UPDATE", UUID.randomUUID().toString(),
                before, after, input.changeReason, input.dataDate, user.id());
        log(id, "UPDATE", "PUT", "/api/basic-info/staffing-ledgers/" + id,
                before, after, user.id());
        return after;
    }

    @Transactional
    public List<StaffingDtos.ListItem> batch(StaffingDtos.BatchRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            bad("EMPTY_BATCH", "请选择需要批量修改的台账");
        }
        if (request.items().size() > MAX_BATCH_SIZE) {
            bad("BATCH_TOO_LARGE", "单次批量修改最多50个部门");
        }
        String date = date(request.dataDate());
        String reason = required(request.changeReason(), "变更原因");
        Set<Long> ids = new HashSet<>();
        List<BatchPrepared> prepared = new ArrayList<>();
        for (StaffingDtos.BatchItem item : request.items()) {
            if (item == null || item.id() == null || !ids.add(item.id())) {
                bad("INVALID_BATCH_ITEM", "批量修改包含空值或重复台账");
            }
            StaffingDtos.ListItem before = requireLedger(item.id());
            StaffingDtos.SaveRequest save = new StaffingDtos.SaveRequest(
                    before.orgUnitId(), item.approvedStaffing(), item.actualStaffing(),
                    item.leadershipPositionsApproved(), item.leadershipPositionsOccupied(),
                    item.externalStaff(), date, reason, item.remarks(), item.versionNo());
            prepared.add(new BatchPrepared(before, normalize(save, false)));
            requireMaintainableUnit(before.orgUnitId());
        }
        CurrentUserResponse user = currentUserService.getCurrentUser();
        String group = UUID.randomUUID().toString();
        List<StaffingDtos.ListItem> result = new ArrayList<>();
        for (BatchPrepared item : prepared) {
            var before = item.before;
            var input = item.input;
            if (repository.updateLedger(before.id(), input.write(before.orgUnitId()),
                    user.id(), requireVersion(input.versionNo)) != 1) {
                conflict("STALE_VERSION", before.unitName() + "台账已变化，请刷新后重试");
            }
            UnitRow unit = repository.findUnit(before.orgUnitId()).orElseThrow();
            if (!Objects.equals(unit.approvedStaffing(), input.approvedStaffing)) {
                repository.updateApprovedStaffing(unit.id(), input.approvedStaffing, user.id());
            }
            StaffingDtos.ListItem after = requireLedger(before.id());
            insertChange(before.id(), before.orgUnitId(), "BATCH_UPDATE", group,
                    before, after, reason, date, user.id());
            result.add(after);
        }
        log(result.get(0).id(), "BATCH_UPDATE", "PUT",
                "/api/basic-info/staffing-ledgers/batch", prepared, result, user.id());
        return result;
    }

    public byte[] template() {
        return excel.template();
    }

    public StaffingDtos.ImportResult importFile(MultipartFile file) {
        validateFile(file);
        CurrentUserResponse user = currentUserService.getCurrentUser();
        String batchNo = "STF-" + System.currentTimeMillis();
        long batchId = repository.createImportBatch(
                batchNo, file.getOriginalFilename(), file.getSize(), user.id());
        List<ImportRow> rows;
        try {
            rows = excel.read(file.getBytes());
        } catch (Exception exception) {
            repository.finishImportBatch(batchId, 0, 0, 0, 0, "FAILED");
            if (exception instanceof StaffingException staffingException) {
                throw staffingException;
            }
            throw new StaffingException("READ_FAILED", "读取Excel失败",
                    HttpStatus.BAD_REQUEST);
        }
        List<StaffingDtos.ImportError> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int success = 0;
        for (ImportRow row : rows) {
            try {
                if (row.errorMessage() != null) {
                    throw new StaffingException(
                            "INVALID_ROW", row.errorMessage(), HttpStatus.BAD_REQUEST);
                }
                if (row.orgUnitCode() == null || !seen.add(row.orgUnitCode().toUpperCase())) {
                    throw new StaffingException("DUPLICATE_CODE",
                            "机构编码为空或在文件中重复", HttpStatus.BAD_REQUEST);
                }
                UnitRow unit = repository.findUnitByCode(row.orgUnitCode())
                        .orElseThrow(() -> new StaffingException(
                                "UNIT_NOT_FOUND", "机构编码不存在", HttpStatus.BAD_REQUEST));
                if (row.orgUnitName() != null && !row.orgUnitName().equals(unit.name())) {
                    warnings.add("第" + row.rowNumber() + "行机构名称与编码不一致，已按编码匹配"
                            + unit.name());
                }
                transactions.executeWithoutResult(status -> importOne(row, unit, user, batchNo));
                success++;
            } catch (Exception exception) {
                String message = exception.getMessage() == null ? "导入失败" : exception.getMessage();
                StaffingDtos.ImportError error = new StaffingDtos.ImportError(
                        row.rowNumber(), row.orgUnitCode(), row.orgUnitName(), message);
                errors.add(error);
                repository.insertImportError(batchId, row.rowNumber(), row.orgUnitCode(),
                        row.orgUnitName(), json(row), message);
            }
        }
        String status = errors.isEmpty() ? "COMPLETED" : success == 0 ? "FAILED" : "PARTIAL";
        repository.finishImportBatch(
                batchId, rows.size(), success, errors.size(), warnings.size(), status);
        log(batchId, "IMPORT", "POST", "/api/basic-info/staffing-ledgers/imports",
                null, Map.of("success", success, "failure", errors.size()), user.id());
        return new StaffingDtos.ImportResult(
                batchId, batchNo, file.getOriginalFilename(), rows.size(), success,
                errors.size(), warnings.size(), status, warnings, errors);
    }

    public StaffingDtos.ImportResult importResult(long batchId) {
        var batch = repository.findImportBatch(batchId)
                .orElseThrow(() -> notFound("IMPORT_NOT_FOUND", "导入批次不存在"));
        return new StaffingDtos.ImportResult(
                batch.id(), batch.batchNo(), batch.fileName(), batch.totalRows(),
                batch.successRows(), batch.failedRows(), batch.warningRows(), batch.status(),
                List.of(), repository.findImportErrors(batchId));
    }

    private void importOne(ImportRow row, UnitRow unit, CurrentUserResponse user, String group) {
        requireMaintainableUnit(unit.id());
        String reason = required(row.changeReason(), "变更原因");
        StaffingDtos.ListItem before = repository.ledgerExistsForUnit(unit.id())
                ? repository.findPage(row.orgUnitCode(), "MAINTAINED", null, 1, 5)
                .items().stream().filter(item -> item.orgUnitId() == unit.id()).findFirst().orElseThrow()
                : null;
        LedgerWrite write = new LedgerWrite(
                unit.id(), row.actualStaffing(), row.leadershipApproved(),
                row.leadershipOccupied(), row.externalStaff(), row.dataDate(),
                row.remarks(), reason);
        long ledgerId;
        if (before == null) {
            ledgerId = repository.insertLedger(write, user.id());
        } else {
            ledgerId = before.id();
            if (repository.updateLedger(ledgerId, write, user.id(), before.versionNo()) != 1) {
                conflict("STALE_VERSION", "台账已变化，请重新导入");
            }
        }
        if (!Objects.equals(unit.approvedStaffing(), row.approvedStaffing())) {
            repository.updateApprovedStaffing(unit.id(), row.approvedStaffing(), user.id());
        }
        StaffingDtos.ListItem after = requireLedger(ledgerId);
        insertChange(ledgerId, unit.id(), "EXCEL_IMPORT", group,
                before, after, reason, row.dataDate(), user.id());
    }

    private Normalized normalize(StaffingDtos.SaveRequest request, boolean create) {
        if (request == null) {
            bad("INVALID_REQUEST", "请求内容不能为空");
        }
        if (create && request.orgUnitId() == null) {
            bad("ORG_UNIT_REQUIRED", "必须选择机构");
        }
        return new Normalized(
                request.orgUnitId(),
                number(request.approvedStaffing(), "核定编制"),
                number(request.actualStaffing(), "实有在编"),
                number(request.leadershipPositionsApproved(), "领导职数核定"),
                number(request.leadershipPositionsOccupied(), "领导职数占用"),
                number(request.externalStaff(), "编外人员"),
                date(request.dataDate()),
                required(request.changeReason(), "变更原因"),
                trim(request.remarks()),
                request.versionNo());
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            bad("FILE_REQUIRED", "请选择Excel文件");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".xlsx")) {
            bad("INVALID_FILE_TYPE", "仅支持.xlsx文件");
        }
        if (file.getSize() > StaffingExcelService.MAX_FILE_SIZE) {
            bad("FILE_TOO_LARGE", "单个Excel文件不能超过10MB");
        }
    }

    private UnitRow requireMaintainableUnit(long id) {
        UnitRow unit = repository.findUnit(id)
                .orElseThrow(() -> notFound("UNIT_NOT_FOUND", "机构不存在"));
        if (!"ACTIVE".equals(unit.status())) {
            conflict("UNIT_INACTIVE", "停用机构只允许查看历史，不能维护台账");
        }
        if (Set.of("ROOT", "GROUP").contains(unit.type())) {
            bad("INVALID_UNIT_TYPE", "ROOT和GROUP节点不能维护编制人员台账");
        }
        return unit;
    }

    private StaffingDtos.ListItem requireLedger(long id) {
        return repository.findById(id)
                .orElseThrow(() -> notFound("LEDGER_NOT_FOUND", "台账不存在"));
    }

    private void insertChange(
            long id, long orgId, String source, String group,
            StaffingDtos.ListItem before, StaffingDtos.ListItem after,
            String reason, String dataDate, long userId
    ) {
        List<String> fields = new ArrayList<>();
        changed(fields, "approvedStaffing", before == null ? null : before.approvedStaffing(),
                after.approvedStaffing());
        changed(fields, "actualStaffing", before == null ? null : before.actualStaffing(),
                after.actualStaffing());
        changed(fields, "leadershipPositionsApproved",
                before == null ? null : before.leadershipPositionsApproved(),
                after.leadershipPositionsApproved());
        changed(fields, "leadershipPositionsOccupied",
                before == null ? null : before.leadershipPositionsOccupied(),
                after.leadershipPositionsOccupied());
        changed(fields, "externalStaff", before == null ? null : before.externalStaff(),
                after.externalStaff());
        if (fields.isEmpty()) {
            fields.add("remarks");
        }
        repository.insertChange(new ChangeWrite(
                group, id, orgId, source,
                before == null ? null : before.approvedStaffing(), after.approvedStaffing(),
                before == null ? null : before.actualStaffing(), after.actualStaffing(),
                before == null ? null : before.leadershipPositionsApproved(),
                after.leadershipPositionsApproved(),
                before == null ? null : before.leadershipPositionsOccupied(),
                after.leadershipPositionsOccupied(),
                before == null ? null : before.externalStaff(), after.externalStaff(),
                String.join(",", fields), dataDate, reason, userId));
    }

    private void changed(List<String> fields, String name, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            fields.add(name);
        }
    }

    private int number(Integer value, String label) {
        if (value == null || value < 0) {
            bad("INVALID_NUMBER", label + "必须是非负整数");
        }
        return value;
    }

    private String date(String value) {
        try {
            return LocalDate.parse(required(value, "数据日期")).toString();
        } catch (DateTimeParseException exception) {
            bad("INVALID_DATE", "数据日期必须为YYYY-MM-DD");
            return null;
        }
    }

    private String required(String value, String label) {
        String result = trim(value);
        if (result == null) {
            bad("REQUIRED_FIELD", label + "不能为空");
        }
        return result;
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int requireVersion(Integer version) {
        if (version == null || version < 1) {
            bad("VERSION_REQUIRED", "缺少有效的版本号");
        }
        return version;
    }

    private void log(
            long id, String action, String method, String path,
            Object before, Object after, long userId
    ) {
        operationLogs.success("M1-4", "STAFFING_LEDGER", id, action, userId,
                method, path, json(before), json(after));
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("操作记录序列化失败", exception);
        }
    }

    private StaffingException notFound(String code, String message) {
        return new StaffingException(code, message, HttpStatus.NOT_FOUND);
    }

    private void bad(String code, String message) {
        throw new StaffingException(code, message, HttpStatus.BAD_REQUEST);
    }

    private void conflict(String code, String message) {
        throw new StaffingException(code, message, HttpStatus.CONFLICT);
    }

    private record Normalized(
            Long orgUnitId, int approvedStaffing, int actualStaffing,
            int leadershipApproved, int leadershipOccupied, int externalStaff,
            String dataDate, String changeReason, String remarks, Integer versionNo
    ) {
        LedgerWrite write() {
            return write(orgUnitId);
        }

        LedgerWrite write(long id) {
            return new LedgerWrite(
                    id, actualStaffing, leadershipApproved, leadershipOccupied,
                    externalStaff, dataDate, remarks, changeReason);
        }
    }

    private record BatchPrepared(StaffingDtos.ListItem before, Normalized input) {
    }
}
