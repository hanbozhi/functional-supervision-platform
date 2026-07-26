package com.zhineng.platform.basicinfo.indicator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhineng.platform.basicinfo.indicator.dto.IndicatorDtos;
import com.zhineng.platform.basicinfo.indicator.repository.IndicatorRepository;
import com.zhineng.platform.common.audit.OperationLogRepository;
import com.zhineng.platform.common.user.dto.CurrentUserResponse;
import com.zhineng.platform.common.user.service.CurrentUserService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Transactional
public class IndicatorService {
    private static final Pattern CODE = Pattern.compile("[A-Z0-9][A-Z0-9_.-]{0,63}");
    private static final Set<String> ITEM_TYPES = Set.of("COMMON", "CUSTOM");
    private static final Set<String> RULE_TYPES =
            Set.of("THRESHOLD_DEDUCTION", "STEP_SCORE", "VETO");
    private static final Set<String> ACTIVE_STATUSES = Set.of("ACTIVE", "INACTIVE");

    private final IndicatorRepository repository;
    private final CurrentUserService currentUser;
    private final OperationLogRepository logs;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;

    public IndicatorService(
            IndicatorRepository repository,
            CurrentUserService currentUser,
            OperationLogRepository logs,
            ObjectMapper mapper,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.logs = logs;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public List<Map<String, Object>> systems(String keyword, Integer year, String status) {
        validateOptionalStatus(status);
        return repository.systems(keyword, year, status);
    }

    public Map<String, Object> system(long id) {
        Map<String, Object> system = requireSystem(id);
        system.put("versions", repository.versions(id, null, null));
        return system;
    }

    public Map<String, Object> createSystem(IndicatorDtos.SystemRequest request) {
        if (request == null || request.evaluationYear() == null) {
            bad("YEAR_REQUIRED", "必须填写年度");
        }
        int year = request.evaluationYear();
        if (year < 1900 || year > 2999) bad("INVALID_YEAR", "年度无效");
        String code = code(request.systemCode(), "体系编码");
        String name = required(request.systemName(), "体系名称");
        String orgType = required(request.applicableOrgType(), "适用机构类型");
        if (repository.systemCodeExists(code)) conflict("DUPLICATE_SYSTEM_CODE", "体系编码已存在");
        CurrentUserResponse user = currentUser.getCurrentUser();
        return transactions.execute(status -> {
            long systemId = repository.insertSystem(
                    code, name, orgType, trim(request.description()), user.id());
            long versionId = repository.insertVersion(
                    systemId, year, 1, year + "年度初始版", null, user.id());
            Map<String, Object> result = system(systemId);
            result.put("currentVersionId", versionId);
            log("M1-7", "INDICATOR_SYSTEM", systemId, "CREATE", "POST",
                    "/api/basic-info/indicator-systems", null, result, user.id());
            return result;
        });
    }

    public List<Map<String, Object>> versions(Long systemId, Integer year, String status) {
        return repository.versions(systemId, year, status);
    }

    public Map<String, Object> version(long id) {
        Map<String, Object> version = requireVersion(id);
        version.put("tree", tree(id));
        version.put("rules", repository.rules(id, null));
        return version;
    }

    public List<Map<String, Object>> tree(long versionId) {
        requireVersion(versionId);
        List<Map<String, Object>> flat = repository.items(versionId);
        Map<Long, Map<String, Object>> byId = new LinkedHashMap<>();
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> row : flat) {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            copy.put("children", new ArrayList<Map<String, Object>>());
            byId.put(number(copy.get("id")), copy);
        }
        for (Map<String, Object> row : byId.values()) {
            Object parent = row.get("parent_id");
            if (parent == null) roots.add(row);
            else {
                Map<String, Object> parentRow = byId.get(number(parent));
                if (parentRow != null) children(parentRow).add(row);
            }
        }
        return roots;
    }

    public Map<String, Object> createItem(IndicatorDtos.ItemRequest request) {
        if (request == null || request.versionId() == null
                || request.indicatorLevel() == null) {
            bad("ITEM_FIELDS_REQUIRED", "缺少版本或指标层级");
        }
        Map<String, Object> version = requireDraft(request.versionId());
        ItemValues values = validateItem(request, null, version);
        CurrentUserResponse user = currentUser.getCurrentUser();
        long id = repository.insertItem(
                request.versionId(), values.parentId, request.indicatorLevel(),
                values.code, values.name, values.score, values.weight, values.type,
                values.method, values.sortOrder, user.id());
        Map<String, Object> after = requireItem(id);
        log("M1-7", "INDICATOR_ITEM", id, "CREATE", "POST",
                "/api/basic-info/indicator-items", null, after, user.id());
        return after;
    }

    public Map<String, Object> updateItem(long id, IndicatorDtos.ItemRequest request) {
        Map<String, Object> before = requireItem(id);
        if (request == null || request.indicatorLevel() == null
                || request.indicatorLevel() != (int) number(before.get("indicator_level"))) {
            bad("INDICATOR_LEVEL_IMMUTABLE", "指标层级不可直接修改");
        }
        Map<String, Object> version = requireDraft(number(before.get("version_id")));
        ItemValues values = validateItem(request, id, version);
        if (repository.updateItem(
                id, values.parentId, values.code, values.name, values.score, values.weight,
                values.type, values.method, values.sortOrder,
                requireRowVersion(request == null ? null : request.rowVersion()),
                currentUser.getCurrentUser().id()) != 1) {
            conflict("STALE_ITEM", "指标已变化，请刷新后重试");
        }
        Map<String, Object> after = requireItem(id);
        log("M1-8", "INDICATOR_ITEM", id, "UPDATE", "PUT",
                "/api/basic-info/indicator-items/" + id, before, after,
                currentUser.getCurrentUser().id());
        return after;
    }

    public Map<String, Object> itemStatus(long id, IndicatorDtos.StatusRequest request) {
        Map<String, Object> before = requireItem(id);
        requireDraft(number(before.get("version_id")));
        String status = activeStatus(request == null ? null : request.status());
        if ("INACTIVE".equals(status) && repository.itemChildCount(id) > 0) {
            conflict("ACTIVE_CHILDREN_EXIST", "存在启用子指标，不能停用");
        }
        CurrentUserResponse user = currentUser.getCurrentUser();
        if (repository.itemStatus(
                id, status, requireRowVersion(request.rowVersion()), user.id()) != 1) {
            conflict("STALE_ITEM", "指标已变化，请刷新后重试");
        }
        Map<String, Object> after = requireItem(id);
        log("M1-8", "INDICATOR_ITEM", id, status, "PUT",
                "/api/basic-info/indicator-items/" + id + "/status",
                before, after, user.id());
        return after;
    }

    public Map<String, Object> publish(long versionId, Integer rowVersion) {
        Map<String, Object> before = requireDraft(versionId);
        validatePublishable(versionId);
        CurrentUserResponse user = currentUser.getCurrentUser();
        if (repository.publish(versionId, requireRowVersion(rowVersion), user.id()) != 1) {
            conflict("STALE_VERSION", "版本已变化，请刷新后重试");
        }
        Map<String, Object> after = version(versionId);
        log("M1-7", "INDICATOR_VERSION", versionId, "PUBLISH", "POST",
                "/api/basic-info/indicator-versions/" + versionId + "/publish",
                before, after, user.id());
        return after;
    }

    public Map<String, Object> archiveVersion(long versionId, Integer rowVersion) {
        Map<String, Object> before = requireVersion(versionId);
        if (!"PUBLISHED".equals(before.get("status"))) {
            conflict("VERSION_NOT_PUBLISHED", "仅已发布版本可以归档");
        }
        CurrentUserResponse user = currentUser.getCurrentUser();
        if (repository.archive(versionId, requireRowVersion(rowVersion), user.id()) != 1) {
            conflict("STALE_VERSION", "版本已变化，请刷新后重试");
        }
        Map<String, Object> after = version(versionId);
        log("M1-7", "INDICATOR_VERSION", versionId, "ARCHIVE", "POST",
                "/api/basic-info/indicator-versions/" + versionId + "/archive",
                before, after, user.id());
        return after;
    }

    public Map<String, Object> copyVersion(
            long sourceId, IndicatorDtos.CopyVersionRequest request
    ) {
        Map<String, Object> source = requireVersion(sourceId);
        if (request == null || request.targetYear() == null) bad("TARGET_YEAR_REQUIRED", "目标年度必填");
        int year = request.targetYear();
        if (year < 1900 || year > 2999) bad("INVALID_YEAR", "目标年度无效");
        CurrentUserResponse user = currentUser.getCurrentUser();
        return transactions.execute(status -> {
            int no = repository.nextVersionNo(number(source.get("system_id")), year);
            String name = trim(request.versionName());
            long newId = repository.insertVersion(
                    number(source.get("system_id")), year, no,
                    name == null ? year + "年度V" + no : name, sourceId, user.id());
            cloneContent(sourceId, newId, user.id());
            Map<String, Object> after = version(newId);
            log("M1-7", "INDICATOR_VERSION", newId, "COPY", "POST",
                    "/api/basic-info/indicator-versions/" + sourceId + "/copy",
                    source, after, user.id());
            return after;
        });
    }

    public List<Map<String, Object>> rules(Long versionId, Long indicatorId) {
        return repository.rules(versionId, indicatorId);
    }

    public Map<String, Object> createRule(IndicatorDtos.RuleRequest request) {
        RuleValues values = validateRule(request, null);
        CurrentUserResponse user = currentUser.getCurrentUser();
        long id = repository.insertRule(
                values.indicatorId, values.type, values.name, json(values.config),
                values.description, values.sortOrder, user.id());
        Map<String, Object> after = requireRule(id);
        log("M1-8", "INDICATOR_RULE", id, "CREATE", "POST",
                "/api/basic-info/indicator-rules", null, after, user.id());
        return after;
    }

    public Map<String, Object> updateRule(long id, IndicatorDtos.RuleRequest request) {
        Map<String, Object> before = requireRule(id);
        RuleValues values = validateRule(request, id);
        CurrentUserResponse user = currentUser.getCurrentUser();
        if (repository.updateRule(
                id, values.indicatorId, values.type, values.name, json(values.config), values.description,
                values.sortOrder, requireRowVersion(request.rowVersion()), user.id()) != 1) {
            conflict("STALE_RULE", "规则已变化，请刷新后重试");
        }
        Map<String, Object> after = requireRule(id);
        log("M1-8", "INDICATOR_RULE", id, "UPDATE", "PUT",
                "/api/basic-info/indicator-rules/" + id, before, after, user.id());
        return after;
    }

    public Map<String, Object> ruleStatus(long id, IndicatorDtos.StatusRequest request) {
        Map<String, Object> before = requireRule(id);
        if (!"DRAFT".equals(before.get("version_status"))) {
            conflict("VERSION_READ_ONLY", "已发布或已归档版本只读");
        }
        String status = activeStatus(request == null ? null : request.status());
        CurrentUserResponse user = currentUser.getCurrentUser();
        if (repository.ruleStatus(
                id, status, requireRowVersion(request.rowVersion()), user.id()) != 1) {
            conflict("STALE_RULE", "规则已变化，请刷新后重试");
        }
        Map<String, Object> after = requireRule(id);
        log("M1-8", "INDICATOR_RULE", id, status, "PUT",
                "/api/basic-info/indicator-rules/" + id + "/status",
                before, after, user.id());
        return after;
    }

    public List<Map<String, Object>> templates(String keyword, String orgType, String status) {
        validateOptionalStatus(status);
        return repository.templates(keyword, orgType, status);
    }

    public Map<String, Object> template(long id) {
        Map<String, Object> template = requireTemplate(id);
        template.put("snapshot", parse(String.valueOf(template.get("snapshot_json"))));
        template.remove("snapshot_json");
        return template;
    }

    public Map<String, Object> createTemplate(IndicatorDtos.TemplateRequest request) {
        if (request == null || request.sourceVersionId() == null) {
            bad("SOURCE_VERSION_REQUIRED", "请选择指标版本");
        }
        validatePublishable(request.sourceVersionId());
        String code = code(request.templateCode(), "模板编码");
        if (repository.templateCodeExists(code)) conflict("DUPLICATE_TEMPLATE_CODE", "模板编码已存在");
        Map<String, Object> version = requireVersion(request.sourceVersionId());
        String name = required(request.templateName(), "模板名称");
        String orgType = request.applicableOrgType() == null
                ? String.valueOf(version.get("applicable_org_type"))
                : required(request.applicableOrgType(), "适用机构类型");
        Map<String, Object> snapshot = snapshot(request.sourceVersionId());
        CurrentUserResponse user = currentUser.getCurrentUser();
        long id = repository.insertTemplate(
                code, name, orgType, trim(request.description()), json(snapshot),
                request.sourceVersionId(), repository.items(request.sourceVersionId()).size(),
                user.id());
        Map<String, Object> after = template(id);
        log("M1-9", "INDICATOR_TEMPLATE", id, "CREATE", "POST",
                "/api/basic-info/indicator-templates", null, after, user.id());
        return after;
    }

    public Map<String, Object> copyTemplate(
            long id, IndicatorDtos.TemplateCopyRequest request
    ) {
        Map<String, Object> source = requireTemplate(id);
        String code = code(request == null ? null : request.templateCode(), "模板编码");
        String name = required(request == null ? null : request.templateName(), "模板名称");
        if (repository.templateCodeExists(code)) conflict("DUPLICATE_TEMPLATE_CODE", "模板编码已存在");
        CurrentUserResponse user = currentUser.getCurrentUser();
        long newId = repository.insertTemplate(
                code, name, String.valueOf(source.get("applicable_org_type")),
                text(source.get("description")),
                String.valueOf(source.get("snapshot_json")),
                source.get("source_version_id") == null ? null : number(source.get("source_version_id")),
                (int) number(source.get("indicator_count")), user.id());
        Map<String, Object> after = template(newId);
        log("M1-9", "INDICATOR_TEMPLATE", newId, "COPY", "POST",
                "/api/basic-info/indicator-templates/" + id + "/copy",
                source, after, user.id());
        return after;
    }

    public Map<String, Object> templateStatus(long id, IndicatorDtos.StatusRequest request) {
        Map<String, Object> before = requireTemplate(id);
        String status = activeStatus(request == null ? null : request.status());
        CurrentUserResponse user = currentUser.getCurrentUser();
        if (repository.templateStatus(
                id, status, requireRowVersion(request.rowVersion()), user.id()) != 1) {
            conflict("STALE_TEMPLATE", "模板已变化，请刷新后重试");
        }
        Map<String, Object> after = template(id);
        log("M1-9", "INDICATOR_TEMPLATE", id, status, "PUT",
                "/api/basic-info/indicator-templates/" + id + "/status",
                before, after, user.id());
        return after;
    }

    public Map<String, Object> initializeFromTemplate(
            long templateId, IndicatorDtos.TemplateInitializeRequest request
    ) {
        Map<String, Object> template = requireTemplate(templateId);
        if (!"ACTIVE".equals(template.get("status"))) conflict("TEMPLATE_INACTIVE", "模板已停用");
        if (request == null || request.evaluationYear() == null) bad("YEAR_REQUIRED", "年度必填");
        if (request.evaluationYear() < 1900 || request.evaluationYear() > 2999) {
            bad("INVALID_YEAR", "年度无效");
        }
        String systemCode = code(request.systemCode(), "体系编码");
        if (repository.systemCodeExists(systemCode)) conflict("DUPLICATE_SYSTEM_CODE", "体系编码已存在");
        String systemName = required(request.systemName(), "体系名称");
        Map<String, Object> snapshot = parse(String.valueOf(template.get("snapshot_json")));
        CurrentUserResponse user = currentUser.getCurrentUser();
        return transactions.execute(status -> {
            long systemId = repository.insertSystem(
                    systemCode, systemName,
                    request.applicableOrgType() == null
                            ? String.valueOf(template.get("applicable_org_type"))
                            : required(request.applicableOrgType(), "适用机构类型"),
                    trim(request.description()), user.id());
            long versionId = repository.insertVersion(
                    systemId, request.evaluationYear(), 1,
                    request.evaluationYear() + "年度模板初始化版", null, user.id());
            restoreSnapshot(snapshot, versionId, user.id());
            Map<String, Object> after = version(versionId);
            log("M1-9", "INDICATOR_SYSTEM", systemId, "INITIALIZE_FROM_TEMPLATE", "POST",
                    "/api/basic-info/indicator-templates/" + templateId + "/initialize",
                    template, after, user.id());
            return after;
        });
    }

    private void cloneContent(long sourceVersionId, long targetVersionId, long userId) {
        List<Map<String, Object>> sourceItems = repository.items(sourceVersionId);
        Map<Long, Long> ids = new HashMap<>();
        for (int level = 1; level <= 3; level++) {
            for (Map<String, Object> item : sourceItems) {
                if (number(item.get("indicator_level")) != level) continue;
                Long oldParent = item.get("parent_id") == null ? null : number(item.get("parent_id"));
                long newId = repository.insertItem(
                        targetVersionId, oldParent == null ? null : ids.get(oldParent), level,
                        String.valueOf(item.get("indicator_code")),
                        String.valueOf(item.get("indicator_name")),
                        decimal(item.get("standard_score")), decimal(item.get("weight")),
                        String.valueOf(item.get("indicator_type")),
                        text(item.get("evaluation_method")),
                        (int) number(item.get("sort_order")), userId);
                ids.put(number(item.get("id")), newId);
                if ("INACTIVE".equals(item.get("status"))) {
                    repository.itemStatus(newId, "INACTIVE", 0, userId);
                }
            }
        }
        for (Map<String, Object> rule : repository.rules(sourceVersionId, null)) {
            long newRuleId = repository.insertRule(
                    ids.get(number(rule.get("indicator_id"))),
                    String.valueOf(rule.get("rule_type")), String.valueOf(rule.get("rule_name")),
                    String.valueOf(rule.get("config_json")), text(rule.get("description")),
                    (int) number(rule.get("sort_order")), userId);
            if ("INACTIVE".equals(rule.get("status"))) {
                repository.ruleStatus(newRuleId, "INACTIVE", 0, userId);
            }
        }
    }

    private Map<String, Object> snapshot(long versionId) {
        List<Map<String, Object>> itemSnapshots = new ArrayList<>();
        for (Map<String, Object> item : repository.items(versionId)) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (String key : List.of(
                    "id", "parent_id", "indicator_level", "indicator_code", "indicator_name",
                    "standard_score", "weight", "indicator_type", "evaluation_method",
                    "sort_order", "status")) copy.put(key, item.get(key));
            itemSnapshots.add(copy);
        }
        List<Map<String, Object>> ruleSnapshots = new ArrayList<>();
        for (Map<String, Object> rule : repository.rules(versionId, null)) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (String key : List.of(
                    "indicator_id", "rule_type", "rule_name", "config_json",
                    "description", "sort_order", "status")) copy.put(key, rule.get(key));
            ruleSnapshots.add(copy);
        }
        return Map.of("items", itemSnapshots, "rules", ruleSnapshots);
    }

    @SuppressWarnings("unchecked")
    private void restoreSnapshot(Map<String, Object> snapshot, long versionId, long userId) {
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) snapshot.getOrDefault("items", List.of());
        Map<Long, Long> ids = new HashMap<>();
        for (int level = 1; level <= 3; level++) {
            for (Map<String, Object> item : items) {
                if (number(item.get("indicator_level")) != level) continue;
                Long oldParent = item.get("parent_id") == null ? null : number(item.get("parent_id"));
                long newId = repository.insertItem(
                        versionId, oldParent == null ? null : ids.get(oldParent), level,
                        String.valueOf(item.get("indicator_code")),
                        String.valueOf(item.get("indicator_name")),
                        decimal(item.get("standard_score")), decimal(item.get("weight")),
                        String.valueOf(item.get("indicator_type")),
                        text(item.get("evaluation_method")),
                        (int) number(item.get("sort_order")), userId);
                ids.put(number(item.get("id")), newId);
                if ("INACTIVE".equals(item.get("status"))) {
                    repository.itemStatus(newId, "INACTIVE", 0, userId);
                }
            }
        }
        List<Map<String, Object>> rules =
                (List<Map<String, Object>>) snapshot.getOrDefault("rules", List.of());
        for (Map<String, Object> rule : rules) {
            long newId = repository.insertRule(
                    ids.get(number(rule.get("indicator_id"))),
                    String.valueOf(rule.get("rule_type")), String.valueOf(rule.get("rule_name")),
                    String.valueOf(rule.get("config_json")), text(rule.get("description")),
                    (int) number(rule.get("sort_order")), userId);
            if ("INACTIVE".equals(rule.get("status"))) {
                repository.ruleStatus(newId, "INACTIVE", 0, userId);
            }
        }
    }

    private void validatePublishable(long versionId) {
        requireVersion(versionId);
        List<Map<String, Object>> active = repository.items(versionId).stream()
                .filter(item -> "ACTIVE".equals(item.get("status"))).toList();
        if (active.isEmpty()) conflict("EMPTY_INDICATOR_TREE", "指标树不能为空");
        Map<Long, Map<String, Object>> byId = new HashMap<>();
        active.forEach(item -> byId.put(number(item.get("id")), item));
        List<Map<String, Object>> roots = active.stream()
                .filter(item -> number(item.get("indicator_level")) == 1).toList();
        requireWeight100(roots, "一级指标");
        for (Map<String, Object> item : active) {
            int level = (int) number(item.get("indicator_level"));
            if (level == 1 && item.get("parent_id") != null) {
                conflict("INVALID_TREE", "一级指标不能设置父级");
            }
            if (level > 1) {
                Map<String, Object> parent = byId.get(number(item.get("parent_id")));
                if (parent == null || number(parent.get("indicator_level")) != level - 1) {
                    conflict("INVALID_TREE", "启用指标必须关联启用的直接上级");
                }
            }
            if (level < 3) {
                List<Map<String, Object>> children = active.stream()
                        .filter(child -> child.get("parent_id") != null
                                && number(child.get("parent_id")) == number(item.get("id")))
                        .toList();
                if (children.isEmpty()) conflict("INCOMPLETE_TREE", "每个启用分组指标必须有启用子指标");
                requireWeight100(children, String.valueOf(item.get("indicator_name")) + "的子指标");
            } else if (decimal(item.get("standard_score")) <= 0) {
                conflict("INVALID_STANDARD_SCORE", "三级指标标准分必须大于0");
            }
        }
    }

    private void requireWeight100(List<Map<String, Object>> items, String label) {
        double sum = items.stream().mapToDouble(item -> decimal(item.get("weight"))).sum();
        if (Math.abs(sum - 100) > 0.001) {
            conflict("INVALID_WEIGHT_SUM", label + "启用权重之和必须为100%，当前为" + sum + "%");
        }
    }

    private ItemValues validateItem(
            IndicatorDtos.ItemRequest request, Long currentId, Map<String, Object> version
    ) {
        if (request == null || request.indicatorLevel() == null) bad("ITEM_FIELDS_REQUIRED", "指标信息不完整");
        int level = request.indicatorLevel();
        if (level < 1 || level > 3) bad("INVALID_INDICATOR_LEVEL", "指标层级必须为1、2或3");
        String code = code(request.indicatorCode(), "指标编码");
        if (repository.itemCodeExists(number(version.get("id")), code, currentId)) {
            conflict("DUPLICATE_INDICATOR_CODE", "当前版本指标编码已存在");
        }
        Long parentId = request.parentId();
        if (level == 1 && parentId != null) bad("INVALID_PARENT", "一级指标不能设置父级");
        if (level > 1) {
            if (parentId == null) bad("PARENT_REQUIRED", "二级和三级指标必须选择父级");
            Map<String, Object> parent = requireItem(parentId);
            if (number(parent.get("version_id")) != number(version.get("id"))
                    || number(parent.get("indicator_level")) != level - 1
                    || !"ACTIVE".equals(parent.get("status"))) {
                bad("INVALID_PARENT", "父指标必须是同版本启用的直接上级");
            }
            if (currentId != null && currentId.equals(parentId)) bad("INVALID_PARENT", "不能选择自身为父级");
        }
        double score = nonnegative(request.standardScore(), 0, "标准分");
        if (level < 3) score = 0;
        double weight = request.weight() == null ? 0 : request.weight();
        if (weight < 0 || weight > 100) bad("INVALID_WEIGHT", "权重必须在0到100之间");
        String type = request.indicatorType() == null ? "COMMON"
                : request.indicatorType().toUpperCase(Locale.ROOT);
        if (!ITEM_TYPES.contains(type)) bad("INVALID_INDICATOR_TYPE", "指标类型无效");
        return new ItemValues(parentId, code, required(request.indicatorName(), "指标名称"),
                score, weight, type, trim(request.evaluationMethod()),
                request.sortOrder() == null ? 0 : Math.max(0, request.sortOrder()));
    }

    private RuleValues validateRule(IndicatorDtos.RuleRequest request, Long currentId) {
        if (request == null || request.indicatorId() == null) bad("INDICATOR_REQUIRED", "请选择三级指标");
        Map<String, Object> item = requireItem(request.indicatorId());
        requireDraft(number(item.get("version_id")));
        if (number(item.get("indicator_level")) != 3) {
            bad("RULE_ONLY_FOR_LEVEL3", "评分规则只能关联三级指标");
        }
        String type = request.ruleType() == null ? "" : request.ruleType().toUpperCase(Locale.ROOT);
        if (!RULE_TYPES.contains(type)) bad("INVALID_RULE_TYPE", "评分规则类型无效");
        Map<String, Object> config = request.config() == null ? Map.of() : request.config();
        validateRuleConfig(type, config);
        return new RuleValues(request.indicatorId(), type,
                required(request.ruleName(), "规则名称"), config, trim(request.description()),
                request.sortOrder() == null ? 0 : Math.max(0, request.sortOrder()));
    }

    @SuppressWarnings("unchecked")
    private void validateRuleConfig(String type, Map<String, Object> config) {
        switch (type) {
            case "THRESHOLD_DEDUCTION" -> {
                requireNumber(config, "threshold", "阈值");
                if (requireNumber(config, "deduction", "扣分值") < 0) {
                    bad("INVALID_RULE_CONFIG", "扣分值不能为负数");
                }
            }
            case "STEP_SCORE" -> {
                Object stepsValue = config.get("steps");
                if (!(stepsValue instanceof List<?>) || ((List<?>) stepsValue).isEmpty()) {
                    bad("INVALID_RULE_CONFIG", "阶梯评分至少配置一个阶梯");
                }
                for (Object step : (List<?>) stepsValue) {
                    if (!(step instanceof Map<?, ?>)) bad("INVALID_RULE_CONFIG", "阶梯格式无效");
                    Map<String, Object> stepMap = (Map<String, Object>) step;
                    double rate = requireNumber(stepMap, "scoreRate", "得分比例");
                    if (rate < 0 || rate > 100) bad("INVALID_RULE_CONFIG", "得分比例必须在0到100之间");
                    requireNumber(stepMap, "min", "阶梯下限");
                }
            }
            case "VETO" -> {
                if (!(config.get("condition") instanceof String condition)
                        || trim(condition) == null) {
                    bad("INVALID_RULE_CONFIG", "一票否决条件不能为空");
                }
                if (!(config.get("result") instanceof String result)
                        || trim(result) == null) {
                    bad("INVALID_RULE_CONFIG", "一票否决结果不能为空");
                }
            }
            default -> bad("INVALID_RULE_TYPE", "评分规则类型无效");
        }
    }

    private double requireNumber(Map<String, Object> config, String key, String label) {
        Object value = config.get(key);
        if (!(value instanceof Number number)) bad("INVALID_RULE_CONFIG", label + "必须是数字");
        return ((Number) value).doubleValue();
    }

    private Map<String, Object> requireSystem(long id) {
        Map<String, Object> value = repository.system(id);
        if (value == null) notFound("SYSTEM_NOT_FOUND", "指标体系不存在");
        return value;
    }

    private Map<String, Object> requireVersion(long id) {
        Map<String, Object> value = repository.version(id);
        if (value == null) notFound("VERSION_NOT_FOUND", "指标版本不存在");
        return value;
    }

    private Map<String, Object> requireDraft(long id) {
        Map<String, Object> value = requireVersion(id);
        if (!"DRAFT".equals(value.get("status"))) {
            conflict("VERSION_READ_ONLY", "已发布或已归档版本只读，请复制为新草稿");
        }
        return value;
    }

    private Map<String, Object> requireItem(long id) {
        Map<String, Object> value = repository.item(id);
        if (value == null) notFound("ITEM_NOT_FOUND", "指标不存在");
        return value;
    }

    private Map<String, Object> requireRule(long id) {
        Map<String, Object> value = repository.rule(id);
        if (value == null) notFound("RULE_NOT_FOUND", "评分规则不存在");
        return value;
    }

    private Map<String, Object> requireTemplate(long id) {
        Map<String, Object> value = repository.template(id);
        if (value == null) notFound("TEMPLATE_NOT_FOUND", "指标模板不存在");
        return value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> children(Map<String, Object> row) {
        return (List<Map<String, Object>>) row.get("children");
    }

    private String code(String value, String label) {
        String code = required(value, label).toUpperCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            bad("INVALID_CODE", label + "仅支持1-64位大写字母、数字、点、横线和下划线");
        }
        return code;
    }

    private String required(String value, String label) {
        String result = trim(value);
        if (result == null) bad("REQUIRED_FIELD", label + "不能为空");
        return result;
    }

    private String trim(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private double nonnegative(Double value, double fallback, String label) {
        double result = value == null ? fallback : value;
        if (result < 0) bad("INVALID_NUMBER", label + "不能为负数");
        return result;
    }

    private int requireRowVersion(Integer value) {
        if (value == null || value < 0) bad("ROW_VERSION_REQUIRED", "缺少有效版本号");
        return value;
    }

    private String activeStatus(String status) {
        String value = status == null ? "" : status.toUpperCase(Locale.ROOT);
        if (!ACTIVE_STATUSES.contains(value)) bad("INVALID_STATUS", "状态无效");
        return value;
    }

    private void validateOptionalStatus(String status) {
        if (status != null && !status.isBlank()) activeStatus(status);
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private double decimal(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0D;
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("JSON序列化失败", exception);
        }
    }

    private Map<String, Object> parse(String value) {
        try {
            return mapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("模板快照损坏", exception);
        }
    }

    private void log(
            String module, String type, long id, String action, String method,
            String path, Object before, Object after, long userId
    ) {
        logs.success(module, type, id, action, userId, method, path,
                before == null ? null : json(before), after == null ? null : json(after));
    }

    private void bad(String code, String message) {
        throw new IndicatorException(code, message, HttpStatus.BAD_REQUEST);
    }

    private void conflict(String code, String message) {
        throw new IndicatorException(code, message, HttpStatus.CONFLICT);
    }

    private void notFound(String code, String message) {
        throw new IndicatorException(code, message, HttpStatus.NOT_FOUND);
    }

    private record ItemValues(
            Long parentId, String code, String name, double score, double weight,
            String type, String method, int sortOrder
    ) {}

    private record RuleValues(
            long indicatorId, String type, String name, Map<String, Object> config,
            String description, int sortOrder
    ) {}
}
