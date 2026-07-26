package com.zhineng.platform.basicinfo.corefunction.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhineng.platform.basicinfo.corefunction.dto.CoreFunctionDtos;
import com.zhineng.platform.basicinfo.corefunction.matcher.KeywordDutyMatcher;
import com.zhineng.platform.basicinfo.corefunction.repository.CoreFunctionRepository;
import com.zhineng.platform.basicinfo.corefunction.repository.CoreFunctionRepository.ResultWrite;
import com.zhineng.platform.basicinfo.corefunction.repository.CoreFunctionRepository.RightsRow;
import com.zhineng.platform.common.audit.OperationLogRepository;
import com.zhineng.platform.common.user.dto.CurrentUserResponse;
import com.zhineng.platform.common.user.service.CurrentUserService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoreFunctionService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> RESULT_TYPES = Set.of(
            "MATCHED", "DUTY_MISSING", "UNAPPROVED_NEW_DUTY");
    private static final Set<String> REVIEW_STATUSES = Set.of(
            "PENDING", "CONFIRMED", "REJECTED", "ADJUSTED");

    private final CoreFunctionRepository repository;
    private final KeywordDutyMatcher matcher;
    private final CurrentUserService currentUserService;
    private final OperationLogRepository logs;
    private final ObjectMapper objectMapper;

    public CoreFunctionService(
            CoreFunctionRepository repository,
            KeywordDutyMatcher matcher,
            CurrentUserService currentUserService,
            OperationLogRepository logs,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.matcher = matcher;
        this.currentUserService = currentUserService;
        this.logs = logs;
        this.objectMapper = objectMapper;
    }

    public CoreFunctionDtos.Page functions(
            Long orgId, String keyword, String status, int page, int size
    ) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(5, size));
        var rows = repository.functions(orgId, keyword, status, safePage, safeSize);
        return new CoreFunctionDtos.Page(
                rows.rows(), rows.total(), safePage, safeSize,
                (int) Math.ceil(rows.total() / (double) safeSize));
    }

    public Map<String, Object> function(long id) {
        return requireFunction(id);
    }

    public Map<String, Object> stats(long orgId) {
        requireOrg(orgId);
        return repository.stats(orgId);
    }

    @Transactional
    public Map<String, Object> createFunction(CoreFunctionDtos.FunctionRequest request) {
        long orgId = request == null || request.orgUnitId() == null
                ? failLong("ORG_REQUIRED", "必须选择机构") : request.orgUnitId();
        requireMaintainableOrg(orgId);
        String code = upper(required(request.functionCode(), "职能编码"));
        String name = required(request.functionName(), "职能名称");
        validateCode(code);
        if (repository.functionCodeExists(orgId, code, null)) {
            conflict("DUPLICATE_CODE", "当前机构已存在相同职能编码");
        }
        CurrentUserResponse user = currentUserService.getCurrentUser();
        long id = repository.insertFunction(
                orgId, code, name, trim(request.industryTag()), trim(request.description()),
                nonnegative(request.sortOrder(), 0, "排序"), user.id());
        Map<String, Object> after = requireFunction(id);
        log("CORE_FUNCTION", id, "CREATE", "POST",
                "/api/basic-info/core-functions", null, after, user.id());
        return after;
    }

    @Transactional
    public Map<String, Object> updateFunction(
            long id, CoreFunctionDtos.FunctionRequest request
    ) {
        Map<String, Object> before = requireFunction(id);
        int version = version(request == null ? null : request.versionNo());
        long orgId = number(before, "org_unit_id");
        requireMaintainableOrg(orgId);
        String code = upper(required(request.functionCode(), "职能编码"));
        validateCode(code);
        if (repository.functionCodeExists(orgId, code, id)) {
            conflict("DUPLICATE_CODE", "当前机构已存在相同职能编码");
        }
        CurrentUserResponse user = currentUserService.getCurrentUser();
        if (repository.updateFunction(
                id, code, required(request.functionName(), "职能名称"),
                trim(request.industryTag()), trim(request.description()),
                nonnegative(request.sortOrder(), 0, "排序"), version, user.id()) != 1) {
            conflict("STALE_VERSION", "核心职能已变化，请刷新后重试");
        }
        Map<String, Object> after = requireFunction(id);
        log("CORE_FUNCTION", id, "UPDATE", "PUT",
                "/api/basic-info/core-functions/" + id, before, after, user.id());
        return after;
    }

    @Transactional
    public Map<String, Object> functionStatus(
            long id, CoreFunctionDtos.StatusRequest request
    ) {
        Map<String, Object> before = requireFunction(id);
        requireMaintainableOrg(number(before, "org_unit_id"));
        String status = upper(request == null ? null : request.status());
        if (!Set.of("ACTIVE", "INACTIVE").contains(status)) {
            bad("INVALID_STATUS", "状态只能是ACTIVE或INACTIVE");
        }
        CurrentUserResponse user = currentUserService.getCurrentUser();
        if (repository.updateFunctionStatus(
                id, status, version(request.versionNo()), user.id()) != 1) {
            conflict("STALE_VERSION", "核心职能已变化，请刷新后重试");
        }
        Map<String, Object> after = requireFunction(id);
        log("CORE_FUNCTION", id, "STATUS", "PUT",
                "/api/basic-info/core-functions/" + id + "/status",
                before, after, user.id());
        return after;
    }

    public List<Map<String, Object>> duties(Long functionId, Long orgId, String status) {
        if (functionId != null) requireFunction(functionId);
        if (orgId != null) requireOrg(orgId);
        return repository.duties(functionId, orgId, status);
    }

    @Transactional
    public Map<String, Object> createDuty(CoreFunctionDtos.DutyRequest request) {
        Map<String, Object> function = requireFunction(
                request == null || request.coreFunctionId() == null
                        ? failLong("FUNCTION_REQUIRED", "必须选择核心职能")
                        : request.coreFunctionId());
        long orgId = number(function, "org_unit_id");
        requireMaintainableOrg(orgId);
        CurrentUserResponse user = currentUserService.getCurrentUser();
        String content = required(request.dutyContent(), "职责内容");
        String keywords = normalizedKeywords(request.keywords(), content);
        long id = repository.insertDuty(
                number(function, "id"), orgId, content, keywords, "MANUAL",
                null, content, nonnegative(request.sortOrder(), 0, "排序"), user.id());
        Map<String, Object> after = requireDuty(id);
        log("DUTY_ITEM", id, "CREATE", "POST",
                "/api/basic-info/core-functions/duties", null, after, user.id());
        return after;
    }

    @Transactional
    public Map<String, Object> updateDuty(long id, CoreFunctionDtos.DutyRequest request) {
        Map<String, Object> before = requireDuty(id);
        requireMaintainableOrg(number(before, "org_unit_id"));
        String content = required(request == null ? null : request.dutyContent(), "职责内容");
        CurrentUserResponse user = currentUserService.getCurrentUser();
        if (repository.updateDuty(
                id, content, normalizedKeywords(request.keywords(), content),
                nonnegative(request.sortOrder(), 0, "排序"),
                version(request.versionNo()), user.id()) != 1) {
            conflict("STALE_VERSION", "职责条目已变化，请刷新后重试");
        }
        Map<String, Object> after = requireDuty(id);
        log("DUTY_ITEM", id, "UPDATE", "PUT",
                "/api/basic-info/core-functions/duties/" + id, before, after, user.id());
        return after;
    }

    @Transactional
    public Map<String, Object> dutyStatus(long id, CoreFunctionDtos.StatusRequest request) {
        Map<String, Object> before = requireDuty(id);
        requireMaintainableOrg(number(before, "org_unit_id"));
        String status = upper(request == null ? null : request.status());
        if (!Set.of("ACTIVE", "INACTIVE").contains(status)) {
            bad("INVALID_STATUS", "职责状态只能是ACTIVE或INACTIVE");
        }
        CurrentUserResponse user = currentUserService.getCurrentUser();
        if (repository.updateDutyStatus(
                id, status, version(request.versionNo()), user.id()) != 1) {
            conflict("STALE_VERSION", "职责条目已变化，请刷新后重试");
        }
        Map<String, Object> after = requireDuty(id);
        log("DUTY_ITEM", id, "STATUS", "PUT",
                "/api/basic-info/core-functions/duties/" + id + "/status",
                before, after, user.id());
        return after;
    }

    public Map<String, Object> dutyPreview(long orgId) {
        requireMaintainableOrg(orgId);
        Map<String, Object> source = repository.currentThreeFixed(orgId);
        if (source == null) {
            return Map.of("available", false, "message", "该机构没有已确认三定方案，可手工录入职责",
                    "items", List.of());
        }
        List<Map<String, Object>> items = new ArrayList<>();
        int order = 10;
        for (String content : matcher.splitResponsibilities(
                string(source, "main_responsibilities"))) {
            items.add(new LinkedHashMap<>(Map.of(
                    "dutyContent", content,
                    "keywords", String.join(",", matcher.generateKeywords(content)),
                    "sourceSnippet", content,
                    "sortOrder", order)));
            order += 10;
        }
        return Map.of(
                "available", true, "sourceVersionId", source.get("version_id"),
                "versionLabel", source.get("version_label"), "items", items);
    }

    @Transactional
    public List<Map<String, Object>> importDuties(
            long orgId, CoreFunctionDtos.DutyImportRequest request
    ) {
        requireMaintainableOrg(orgId);
        Map<String, Object> current = repository.currentThreeFixed(orgId);
        if (current == null) {
            conflict("NO_CONFIRMED_PLAN", "该机构没有已确认三定方案");
        }
        if (request == null || request.sourceVersionId() == null
                || request.sourceVersionId() != number(current, "version_id")) {
            conflict("SOURCE_VERSION_CHANGED", "三定方案当前版本已变化，请重新生成预览");
        }
        if (request.items() == null || request.items().isEmpty()) {
            bad("EMPTY_DUTIES", "至少保留一条候选职责");
        }
        for (CoreFunctionDtos.DutyCandidate item : request.items()) {
            Map<String, Object> function = requireFunction(item.coreFunctionId() == null
                    ? failLong("FUNCTION_REQUIRED", "每条候选职责必须选择核心职能")
                    : item.coreFunctionId());
            if (number(function, "org_unit_id") != orgId) {
                bad("FUNCTION_ORG_MISMATCH", "候选职责与核心职能不属于同一机构");
            }
            required(item.dutyContent(), "职责内容");
        }
        CurrentUserResponse user = currentUserService.getCurrentUser();
        repository.supersedeThreeFixedDuties(orgId, user.id());
        List<Map<String, Object>> result = new ArrayList<>();
        for (CoreFunctionDtos.DutyCandidate item : request.items()) {
            String content = required(item.dutyContent(), "职责内容");
            long id = repository.insertDuty(
                    item.coreFunctionId(), orgId, content,
                    normalizedKeywords(item.keywords(), content), "THREE_FIXED",
                    request.sourceVersionId(), trim(item.sourceSnippet()),
                    nonnegative(item.sortOrder(), 0, "排序"), user.id());
            result.add(requireDuty(id));
        }
        log("DUTY_IMPORT", orgId, "IMPORT_THREE_FIXED", "POST",
                "/api/basic-info/core-functions/org-units/" + orgId + "/duty-imports",
                null, Map.of("count", result.size(), "sourceVersionId", request.sourceVersionId()),
                user.id());
        return result;
    }

    public Map<String, Object> mappings(long orgId) {
        Map<String, Object> org = requireOrg(orgId);
        List<Map<String, Object>> departments = repository.rightsDepartments();
        String orgName = string(org, "unit_name");
        String shortName = string(org, "unit_short_name");
        String normalizedName = matcher.normalizeDepartment(orgName);
        String normalizedShort = matcher.normalizeDepartment(shortName);
        for (Map<String, Object> item : departments) {
            String department = string(item, "department_name");
            boolean suggested = matcher.normalizeDepartment(department).equals(normalizedName)
                    || (!normalizedShort.isBlank()
                    && matcher.normalizeDepartment(department).equals(normalizedShort));
            item.put("suggested", suggested);
            item.put("selected", Objects.equals(longValue(item.get("org_unit_id")), orgId));
        }
        return Map.of("organization", org, "departments", departments,
                "unmappedDepartmentCount", repository.unmappedDepartmentCount());
    }

    @Transactional
    public Map<String, Object> autoMappings(long orgId) {
        requireMaintainableOrg(orgId);
        Map<String, Object> data = mappings(orgId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> departments =
                (List<Map<String, Object>>) data.get("departments");
        List<String> selected = departments.stream()
                .filter(item -> Boolean.TRUE.equals(item.get("suggested")))
                .filter(item -> item.get("org_unit_id") == null
                        || Objects.equals(longValue(item.get("org_unit_id")), orgId))
                .map(item -> string(item, "department_name")).toList();
        return saveMappings(orgId, new CoreFunctionDtos.MappingRequest(selected), "AUTO");
    }

    @Transactional
    public Map<String, Object> saveMappings(
            long orgId, CoreFunctionDtos.MappingRequest request
    ) {
        return saveMappings(orgId, request, "MANUAL");
    }

    private Map<String, Object> saveMappings(
            long orgId, CoreFunctionDtos.MappingRequest request, String type
    ) {
        requireMaintainableOrg(orgId);
        List<String> names = request == null || request.departmentNames() == null
                ? List.of() : request.departmentNames().stream()
                .map(this::trim).filter(Objects::nonNull).distinct().toList();
        Set<String> available = new HashSet<>();
        for (Map<String, Object> item : repository.rightsDepartments()) {
            available.add(string(item, "department_name"));
        }
        for (String name : names) {
            if (!available.contains(name)) {
                bad("UNKNOWN_RIGHTS_DEPARTMENT", "权责部门不存在：" + name);
            }
            Map<String, Object> mapping = repository.mappingByDepartment(name);
            if (mapping != null && number(mapping, "org_unit_id") != orgId) {
                conflict("DEPARTMENT_ALREADY_MAPPED",
                        name + "已映射到" + string(mapping, "unit_name"));
            }
        }
        CurrentUserResponse user = currentUserService.getCurrentUser();
        repository.clearMappings(orgId);
        names.forEach(name -> repository.insertMapping(orgId, name, type, user.id()));
        Map<String, Object> after = mappings(orgId);
        log("RIGHTS_MAPPING", orgId, "UPDATE_MAPPING", "PUT",
                "/api/basic-info/core-functions/org-units/" + orgId + "/rights-mappings",
                null, Map.of("departmentNames", names), user.id());
        return after;
    }

    @Transactional
    public Map<String, Object> runMatch(long orgId, CoreFunctionDtos.MatchRequest request) {
        requireMaintainableOrg(orgId);
        List<Map<String, Object>> duties = repository.duties(null, orgId, "ACTIVE").stream()
                .filter(item -> "ACTIVE".equals(string(item, "function_status")))
                .toList();
        if (duties.isEmpty()) {
            conflict("NO_ACTIVE_DUTIES", "没有启用职责，不能启动匹配");
        }
        if (repository.activeMappingCount(orgId) == 0) {
            conflict("NO_ACTIVE_MAPPINGS", "没有启用的权责部门映射，不能启动匹配");
        }
        List<RightsRow> rights = repository.rightsForOrg(orgId);
        if (rights.isEmpty()) {
            conflict("NO_RIGHTS_ITEMS", "当前部门映射下没有权责事项");
        }
        int threshold = request == null || request.threshold() == null
                ? 50 : request.threshold();
        if (threshold < 0 || threshold > 100) {
            bad("INVALID_THRESHOLD", "匹配阈值必须在0到100之间");
        }
        List<PendingResult> pending = new ArrayList<>();
        Set<Long> matchedRights = new HashSet<>();
        int matchedDuty = 0;
        double bestScoreSum = 0;
        int missing = 0;
        for (Map<String, Object> duty : duties) {
            List<String> keywords = matcher.parseKeywords(string(duty, "keywords"));
            List<Candidate> candidates = new ArrayList<>();
            if (!keywords.isEmpty()) {
                for (RightsRow right : rights) {
                    var match = matcher.score(keywords, right.combinedText());
                    if (match.score() >= threshold) {
                        candidates.add(new Candidate(right, match.score(), match.matchedKeywords()));
                    }
                }
            }
            candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
            if (candidates.isEmpty()) {
                missing++;
                pending.add(PendingResult.missing(duty,
                        keywords.isEmpty() ? "关键词为空，待人工处理" : null));
            } else {
                matchedDuty++;
                bestScoreSum += candidates.get(0).score;
                for (Candidate candidate : candidates.stream().limit(3).toList()) {
                    matchedRights.add(candidate.right.id());
                    pending.add(PendingResult.matched(duty, candidate));
                }
            }
        }
        for (RightsRow right : rights) {
            if (!matchedRights.contains(right.id())) {
                pending.add(PendingResult.unapproved(right));
            }
        }
        int unapproved = rights.size() - matchedRights.size();
        double coverage = Math.round(matchedDuty * 10000.0 / duties.size()) / 100.0;
        double rate = Math.round(bestScoreSum * 100.0 / duties.size()) / 100.0;
        CurrentUserResponse user = currentUserService.getCurrentUser();
        Map<String, Object> source = repository.currentThreeFixed(orgId);
        Long sourceVersion = source == null ? null : number(source, "version_id");
        long runId = repository.insertRun(
                orgId, sourceVersion, repository.rightsDatasetSignature(), threshold,
                duties.size(), rights.size(), matchedDuty, missing, unapproved,
                coverage, rate, user.id());
        pending.forEach(item -> repository.insertResult(item.write(runId)));
        Map<String, Object> run = repository.run(runId);
        log("MATCH_RUN", runId, "MATCH", "POST",
                "/api/basic-info/core-functions/org-units/" + orgId + "/match-runs",
                null, run, user.id());
        return run;
    }

    public List<Map<String, Object>> runs(long orgId) {
        requireOrg(orgId);
        return repository.runs(orgId);
    }

    public List<Map<String, Object>> rightsItems(long orgId) {
        requireOrg(orgId);
        return repository.rightsForOrg(orgId).stream().map(item -> Map.<String, Object>of(
                "id", item.id(),
                "department", item.department(),
                "itemName", item.itemName() == null ? "" : item.itemName(),
                "content", item.combinedText()
        )).toList();
    }

    public List<Map<String, Object>> results(
            long runId, String resultType, String reviewStatus
    ) {
        requireRun(runId);
        return repository.results(runId, resultType, reviewStatus);
    }

    @Transactional
    public Map<String, Object> review(
            long resultId, CoreFunctionDtos.ReviewRequest request
    ) {
        Map<String, Object> before = requireResult(resultId);
        long runId = number(before, "run_id");
        Map<String, Object> run = requireRun(runId);
        ValidatedResult value = validateResultRequest(request, number(run, "org_unit_id"));
        CurrentUserResponse user = currentUserService.getCurrentUser();
        if (repository.reviewResult(
                resultId, value.type, value.dutyId, value.rightsId, value.score,
                value.reviewStatus, value.opinion, version(request.versionNo()), user.id(),
                value.dutySnapshot, value.right) != 1) {
            conflict("STALE_VERSION", "匹配结果已变化，请刷新后重试");
        }
        repository.recalculateRun(runId);
        Map<String, Object> after = requireResult(resultId);
        log("MATCH_RESULT", resultId, "REVIEW", "PUT",
                "/api/basic-info/core-functions/match-results/" + resultId + "/review",
                before, after, user.id());
        return after;
    }

    @Transactional
    public Map<String, Object> createManualResult(
            long runId, CoreFunctionDtos.ReviewRequest request
    ) {
        Map<String, Object> run = requireRun(runId);
        ValidatedResult value = validateResultRequest(request, number(run, "org_unit_id"));
        CurrentUserResponse user = currentUserService.getCurrentUser();
        long id = repository.insertManualResult(new ResultWrite(
                runId, value.dutyId, value.rightsId, value.type, "MANUAL",
                value.dutySnapshot, value.right == null ? null : value.right.department(),
                value.right == null ? null : value.right.itemName(),
                value.right == null ? null : value.right.combinedText(),
                0, value.score, null, value.reviewStatus));
        repository.recalculateRun(runId);
        Map<String, Object> after = requireResult(id);
        log("MATCH_RESULT", id, "MANUAL_ADJUST", "POST",
                "/api/basic-info/core-functions/match-runs/" + runId + "/manual-results",
                null, after, user.id());
        return after;
    }

    private ValidatedResult validateResultRequest(
            CoreFunctionDtos.ReviewRequest request, long orgId
    ) {
        if (request == null) bad("INVALID_REQUEST", "请求内容不能为空");
        String type = upper(request.resultType());
        String reviewStatus = upper(request.reviewStatus());
        if (!RESULT_TYPES.contains(type)) bad("INVALID_RESULT_TYPE", "匹配结果类型无效");
        if (!REVIEW_STATUSES.contains(reviewStatus)) {
            bad("INVALID_REVIEW_STATUS", "人工处理状态无效");
        }
        Long dutyId = request.dutyItemId();
        Long rightsId = request.rightsItemId();
        if ("MATCHED".equals(type) && (dutyId == null || rightsId == null)
                || "DUTY_MISSING".equals(type) && (dutyId == null || rightsId != null)
                || "UNAPPROVED_NEW_DUTY".equals(type) && (dutyId != null || rightsId == null)) {
            bad("INCOMPLETE_MATCH_RESULT", "匹配结果两侧数据与结果类型不一致");
        }
        String dutySnapshot = null;
        if (dutyId != null) {
            Map<String, Object> duty = requireDuty(dutyId);
            if (number(duty, "org_unit_id") != orgId) {
                bad("DUTY_ORG_MISMATCH", "职责条目不属于当前机构");
            }
            dutySnapshot = string(duty, "duty_content");
        }
        RightsRow right = null;
        if (rightsId != null) {
            right = repository.rightsForOrg(orgId).stream()
                    .filter(item -> item.id() == rightsId).findFirst()
                    .orElseThrow(() -> new CoreFunctionException(
                            "RIGHTS_ORG_MISMATCH", "权责事项不属于当前机构映射",
                            HttpStatus.BAD_REQUEST));
        }
        double score = request.finalScore() == null ? 0 : request.finalScore();
        if (score < 0 || score > 100) bad("INVALID_SCORE", "最终分值必须在0到100之间");
        String opinion = trim(request.opinion());
        if (Set.of("REJECTED", "ADJUSTED").contains(reviewStatus) && opinion == null) {
            bad("OPINION_REQUIRED", "驳回或调整时必须填写处理意见");
        }
        return new ValidatedResult(
                type, dutyId, rightsId, score, reviewStatus, opinion, dutySnapshot, right);
    }

    private Map<String, Object> requireOrg(long id) {
        Map<String, Object> org = repository.organization(id);
        if (org == null) throw notFound("ORG_NOT_FOUND", "机构不存在");
        return org;
    }

    private Map<String, Object> requireMaintainableOrg(long id) {
        Map<String, Object> org = requireOrg(id);
        if (!"ACTIVE".equals(string(org, "status"))
                || Set.of("ROOT", "GROUP").contains(string(org, "unit_type"))) {
            conflict("ORG_NOT_MAINTAINABLE", "仅启用的业务机构可以维护核心职能");
        }
        return org;
    }

    private Map<String, Object> requireFunction(long id) {
        try { return repository.function(id); }
        catch (Exception exception) { throw notFound("FUNCTION_NOT_FOUND", "核心职能不存在"); }
    }

    private Map<String, Object> requireDuty(long id) {
        try { return repository.duty(id); }
        catch (Exception exception) { throw notFound("DUTY_NOT_FOUND", "职责条目不存在"); }
    }

    private Map<String, Object> requireRun(long id) {
        try { return repository.run(id); }
        catch (Exception exception) { throw notFound("RUN_NOT_FOUND", "匹配运行不存在"); }
    }

    private Map<String, Object> requireResult(long id) {
        try { return repository.result(id); }
        catch (Exception exception) { throw notFound("RESULT_NOT_FOUND", "匹配结果不存在"); }
    }

    private String normalizedKeywords(String requested, String content) {
        List<String> words = matcher.parseKeywords(requested);
        if (words.isEmpty() && (requested == null || requested.isBlank())) {
            words = matcher.generateKeywords(content);
        }
        return String.join(",", words);
    }

    private void validateCode(String code) {
        if (!code.matches("[A-Z0-9][A-Z0-9_-]{1,49}")) {
            bad("INVALID_CODE", "职能编码仅允许大写字母、数字、下划线和短横线");
        }
    }

    private int nonnegative(Integer value, int fallback, String label) {
        int result = value == null ? fallback : value;
        if (result < 0) bad("INVALID_NUMBER", label + "不能小于0");
        return result;
    }

    private int version(Integer value) {
        if (value == null || value < 1) bad("VERSION_REQUIRED", "缺少有效版本号");
        return value;
    }

    private String required(String value, String label) {
        String result = trim(value);
        if (result == null) bad("REQUIRED_FIELD", label + "不能为空");
        return result;
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String upper(String value) {
        String result = trim(value);
        return result == null ? null : result.toUpperCase();
    }

    private long number(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).longValue();
    }

    private Long longValue(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private long failLong(String code, String message) {
        bad(code, message);
        return 0;
    }

    private void log(
            String businessType, long id, String action, String method,
            String path, Object before, Object after, long userId
    ) {
        logs.success("M1-5", businessType, id, action, userId,
                method, path, json(before), json(after));
    }

    private String json(Object value) {
        if (value == null) return null;
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("操作日志序列化失败", exception);
        }
    }

    private CoreFunctionException notFound(String code, String message) {
        return new CoreFunctionException(code, message, HttpStatus.NOT_FOUND);
    }

    private void bad(String code, String message) {
        throw new CoreFunctionException(code, message, HttpStatus.BAD_REQUEST);
    }

    private void conflict(String code, String message) {
        throw new CoreFunctionException(code, message, HttpStatus.CONFLICT);
    }

    private record Candidate(RightsRow right, double score, List<String> keywords) {
    }

    private record ValidatedResult(
            String type, Long dutyId, Long rightsId, double score,
            String reviewStatus, String opinion, String dutySnapshot, RightsRow right
    ) {
    }

    private record PendingResult(
            Long dutyId, Long rightsId, String type, String dutySnapshot,
            RightsRow right, double score, String keywords
    ) {
        static PendingResult matched(Map<String, Object> duty, Candidate candidate) {
            return new PendingResult(
                    ((Number) duty.get("id")).longValue(), candidate.right.id(), "MATCHED",
                    String.valueOf(duty.get("duty_content")), candidate.right,
                    candidate.score, String.join(",", candidate.keywords));
        }

        static PendingResult missing(Map<String, Object> duty, String note) {
            return new PendingResult(
                    ((Number) duty.get("id")).longValue(), null, "DUTY_MISSING",
                    String.valueOf(duty.get("duty_content")), null, 0, note);
        }

        static PendingResult unapproved(RightsRow right) {
            return new PendingResult(
                    null, right.id(), "UNAPPROVED_NEW_DUTY",
                    null, right, 0, null);
        }

        ResultWrite write(long runId) {
            return new ResultWrite(
                    runId, dutyId, rightsId, type, "AUTO", dutySnapshot,
                    right == null ? null : right.department(),
                    right == null ? null : right.itemName(),
                    right == null ? null : right.combinedText(),
                    score, score, keywords, "PENDING");
        }
    }
}
