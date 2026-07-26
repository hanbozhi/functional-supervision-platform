package com.zhineng.platform.basicinfo.orgunit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhineng.platform.basicinfo.orgunit.dto.OrgUnitDtos;
import com.zhineng.platform.basicinfo.orgunit.repository.OrgUnitRepository;
import com.zhineng.platform.basicinfo.orgunit.repository.OrgUnitRepository.UnitRow;
import com.zhineng.platform.basicinfo.orgunit.repository.OrgUnitRepository.UnitWrite;
import com.zhineng.platform.common.audit.OperationLogRepository;
import com.zhineng.platform.common.user.dto.CurrentUserResponse;
import com.zhineng.platform.common.user.service.CurrentUserService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrgUnitService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> UNIT_TYPES = Set.of(
            "ROOT", "GROUP", "OFFICE", "ADMIN_AGENCY", "PUBLIC_INSTITUTION", "OTHER");
    private static final Set<String> UNIT_LEVELS = Set.of("CITY", "DISTRICT", "TOWNSHIP", "OTHER");
    private static final Set<String> NATURES = Set.of(
            "MANAGEMENT_ROOT", "GOVERNMENT_GROUP", "PARTY_AGENCY",
            "GOVERNMENT_AGENCY", "PUBLIC_INSTITUTION", "OTHER");

    private final OrgUnitRepository repository;
    private final OperationLogRepository logs;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public OrgUnitService(
            OrgUnitRepository repository,
            OperationLogRepository logs,
            CurrentUserService currentUserService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.logs = logs;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
    }

    public List<OrgUnitDtos.TreeNode> tree(boolean includeInactive) {
        Map<Long, TreeBuilder> nodes = new LinkedHashMap<>();
        for (UnitRow row : repository.findTreeRows(includeInactive)) {
            nodes.put(row.id(), new TreeBuilder(row));
        }
        List<TreeBuilder> roots = new ArrayList<>();
        for (TreeBuilder node : nodes.values()) {
            TreeBuilder parent = node.row.parentId() == null ? null : nodes.get(node.row.parentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children.add(node);
            }
        }
        return roots.stream().map(TreeBuilder::toDto).toList();
    }

    public OrgUnitDtos.Page page(
            Long parentId,
            String scope,
            String keyword,
            String unitType,
            String unitLevel,
            String nature,
            String status,
            String verificationStatus,
            int page,
            int size
    ) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(5, size));
        if (parentId != null) {
            requireUnit(parentId);
        }
        String safeScope = "DIRECT".equalsIgnoreCase(scope) ? "DIRECT" : "SUBTREE";
        var result = repository.findPage(parentId, safeScope, keyword, unitType, unitLevel,
                nature, status, verificationStatus, safePage, safeSize);
        int pages = (int) Math.ceil(result.total() / (double) safeSize);
        return new OrgUnitDtos.Page(
                result.rows().stream().map(this::toListItem).toList(),
                result.total(), safePage, safeSize, pages);
    }

    public OrgUnitDtos.Stats stats(Long parentId, String scope) {
        if (parentId != null) {
            requireUnit(parentId);
        }
        return repository.stats(parentId,
                "DIRECT".equalsIgnoreCase(scope) ? "DIRECT" : "SUBTREE");
    }

    public OrgUnitDtos.Options options() {
        return new OrgUnitDtos.Options(
                options(new String[][]{
                        {"ROOT", "根节点"}, {"GROUP", "分类节点"}, {"OFFICE", "机关"},
                        {"ADMIN_AGENCY", "行政机关"}, {"PUBLIC_INSTITUTION", "事业单位"},
                        {"OTHER", "其他"}
                }),
                options(new String[][]{
                        {"CITY", "市级"}, {"DISTRICT", "区级"},
                        {"TOWNSHIP", "街道/乡镇"}, {"OTHER", "其他"}
                }),
                options(new String[][]{
                        {"MANAGEMENT_ROOT", "机构编制管理根节点"},
                        {"GOVERNMENT_GROUP", "政府工作部门分组"},
                        {"PARTY_AGENCY", "党委机关"},
                        {"GOVERNMENT_AGENCY", "政府机关"},
                        {"PUBLIC_INSTITUTION", "事业单位"}, {"OTHER", "其他"}
                }),
                options(new String[][]{{"ACTIVE", "启用"}, {"INACTIVE", "停用"}}),
                options(new String[][]{
                        {"PENDING", "待核验"}, {"VERIFIED", "已核验"},
                        {"REJECTED", "核验不通过"}
                })
        );
    }

    public OrgUnitDtos.Detail detail(long id) {
        UnitRow row = requireUnit(id);
        return toDetail(row, repository.findVerifications(id));
    }

    public List<OrgUnitDtos.Verification> verifications(long id) {
        requireUnit(id);
        return repository.findVerifications(id);
    }

    @Transactional
    public OrgUnitDtos.Detail create(OrgUnitDtos.SaveRequest request) {
        UnitWrite write = validateAndNormalize(request, null);
        CurrentUserResponse user = currentUserService.getCurrentUser();
        long id = repository.insert(write, user.id());
        OrgUnitDtos.Detail after = detail(id);
        logs.success("M1-1", "ORG_UNIT", id, "CREATE", user.id(),
                "POST", "/api/basic-info/org-units", null, json(after));
        return after;
    }

    @Transactional
    public OrgUnitDtos.Detail update(long id, OrgUnitDtos.SaveRequest request) {
        UnitRow before = requireUnit(id);
        requireVersion(request.versionNo());
        UnitWrite write = validateAndNormalize(request, id);
        boolean reset = materialChanged(before, write);
        CurrentUserResponse user = currentUserService.getCurrentUser();
        if (repository.update(id, write, user.id(), request.versionNo(), reset) != 1) {
            conflict("STALE_VERSION", "机构已被其他操作修改，请刷新后重试");
        }
        OrgUnitDtos.Detail after = detail(id);
        logs.success("M1-1", "ORG_UNIT", id, "UPDATE", user.id(),
                "PUT", "/api/basic-info/org-units/" + id, json(before), json(after));
        return after;
    }

    @Transactional
    public OrgUnitDtos.Detail updateStatus(long id, OrgUnitDtos.StatusRequest request) {
        UnitRow before = requireUnit(id);
        requireVersion(request.versionNo());
        String status = upper(request.status());
        if (!Set.of("ACTIVE", "INACTIVE").contains(status)) {
            badRequest("INVALID_STATUS", "状态只能是 ACTIVE 或 INACTIVE");
        }
        if (status.equals(before.status())) {
            return detail(id);
        }
        if ("INACTIVE".equals(status) && repository.childCount(id) > 0) {
            conflict("HAS_CHILDREN", "存在子机构，不能停用；请先处理下级机构");
        }
        if ("ACTIVE".equals(status) && before.parentId() != null) {
            UnitRow parent = requireUnit(before.parentId());
            if (!"ACTIVE".equals(parent.status())) {
                conflict("PARENT_INACTIVE", "上级机构已停用，不能启用当前机构");
            }
        }
        CurrentUserResponse user = currentUserService.getCurrentUser();
        if (repository.updateStatus(id, status, user.id(), request.versionNo()) != 1) {
            conflict("STALE_VERSION", "机构已被其他操作修改，请刷新后重试");
        }
        OrgUnitDtos.Detail after = detail(id);
        logs.success("M1-1", "ORG_UNIT", id,
                "ACTIVE".equals(status) ? "ENABLE" : "DISABLE", user.id(),
                "PUT", "/api/basic-info/org-units/" + id + "/status",
                json(before), json(after));
        return after;
    }

    @Transactional
    public OrgUnitDtos.Detail verify(long id, OrgUnitDtos.VerificationRequest request) {
        UnitRow before = requireUnit(id);
        requireVersion(request.versionNo());
        if (!"ACTIVE".equals(before.status())) {
            conflict("UNIT_INACTIVE", "已停用机构不能核验");
        }
        String result = upper(request.result());
        if (!Set.of("VERIFIED", "REJECTED").contains(result)) {
            badRequest("INVALID_VERIFICATION", "核验结果只能是 VERIFIED 或 REJECTED");
        }
        String opinion = trimToNull(request.opinion());
        if ("REJECTED".equals(result) && opinion == null) {
            badRequest("OPINION_REQUIRED", "核验不通过时必须填写核验意见");
        }
        CurrentUserResponse user = currentUserService.getCurrentUser();
        if (repository.verify(id, result, opinion, user.id(), request.versionNo()) != 1) {
            conflict("STALE_VERSION", "机构已被其他操作修改，请刷新后重试");
        }
        repository.insertVerification(id, result, opinion, user.id());
        OrgUnitDtos.Detail after = detail(id);
        logs.success("M1-1", "ORG_UNIT", id, "VERIFY", user.id(),
                "POST", "/api/basic-info/org-units/" + id + "/verifications",
                json(before), json(after));
        return after;
    }

    private UnitWrite validateAndNormalize(OrgUnitDtos.SaveRequest request, Long currentId) {
        if (request == null) {
            badRequest("INVALID_REQUEST", "请求内容不能为空");
        }
        String code = upper(request.unitCode());
        String name = requireText(request.unitName(), "机构名称");
        String type = upper(request.unitType());
        String level = upper(request.unitLevel());
        String nature = upper(request.organizationNature());
        if (code == null) {
            badRequest("UNIT_CODE_REQUIRED", "单位编码不能为空");
        }
        if (!code.matches("[A-Z0-9][A-Z0-9_-]{1,49}")) {
            badRequest("INVALID_UNIT_CODE", "单位编码只能包含大写字母、数字、下划线和短横线");
        }
        if (!UNIT_TYPES.contains(type)) {
            badRequest("INVALID_UNIT_TYPE", "机构类型无效");
        }
        if (!UNIT_LEVELS.contains(level)) {
            badRequest("INVALID_UNIT_LEVEL", "机构层级无效");
        }
        if (!NATURES.contains(nature)) {
            badRequest("INVALID_ORGANIZATION_NATURE", "机构性质无效");
        }
        if (request.approvedStaffing() != null && request.approvedStaffing() < 0) {
            badRequest("INVALID_STAFFING", "核定编制不能小于0");
        }
        if (repository.codeExists(code, currentId)) {
            conflict("DUPLICATE_UNIT_CODE", "单位编码已存在");
        }
        Long parentId = request.parentId();
        if ("ROOT".equals(type)) {
            if (parentId != null) {
                badRequest("ROOT_PARENT_NOT_ALLOWED", "根节点不能设置上级机构");
            }
        } else if (parentId == null) {
            badRequest("PARENT_REQUIRED", "非根节点必须选择上级机构");
        }
        if (parentId != null) {
            if (Objects.equals(parentId, currentId)) {
                conflict("PARENT_CYCLE", "不能将机构自身设置为上级");
            }
            UnitRow parent = requireUnit(parentId);
            if (!"ACTIVE".equals(parent.status())) {
                conflict("PARENT_INACTIVE", "上级机构已停用");
            }
            if (currentId != null && repository.isDescendant(currentId, parentId)) {
                conflict("PARENT_CYCLE", "不能将子机构设置为上级机构");
            }
        }
        return new UnitWrite(parentId, code, name, trimToNull(request.unitShortName()),
                type, level, nature, request.approvedStaffing(),
                request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private boolean materialChanged(UnitRow before, UnitWrite after) {
        return !Objects.equals(before.parentId(), after.parentId())
                || !Objects.equals(before.unitCode(), after.unitCode())
                || !Objects.equals(before.unitName(), after.unitName())
                || !Objects.equals(before.unitType(), after.unitType())
                || !Objects.equals(before.unitLevel(), after.unitLevel())
                || !Objects.equals(before.organizationNature(), after.organizationNature())
                || !Objects.equals(before.approvedStaffing(), after.approvedStaffing());
    }

    private OrgUnitDtos.ListItem toListItem(UnitRow row) {
        return new OrgUnitDtos.ListItem(
                row.id(), row.parentId(), row.parentName(), row.unitCode(), row.unitName(),
                row.unitShortName(), row.unitType(), row.unitLevel(), row.organizationNature(),
                row.approvedStaffing(), row.sortOrder(), row.status(),
                row.verificationStatus(), row.versionNo());
    }

    private OrgUnitDtos.Detail toDetail(UnitRow row, List<OrgUnitDtos.Verification> history) {
        return new OrgUnitDtos.Detail(
                row.id(), row.parentId(), row.parentName(), row.unitCode(), row.unitName(),
                row.unitShortName(), row.unitType(), row.unitLevel(), row.organizationNature(),
                row.approvedStaffing(), row.sortOrder(), row.status(),
                row.verificationStatus(), row.verificationOpinion(),
                row.createdBy(), row.createdByName(), row.createdAt(),
                row.updatedBy(), row.updatedByName(), row.updatedAt(),
                row.verifiedBy(), row.verifiedByName(), row.verifiedAt(),
                row.versionNo(), repository.childCount(row.id()), history);
    }

    private UnitRow requireUnit(long id) {
        return repository.findById(id).orElseThrow(() -> new OrgUnitBusinessException(
                HttpStatus.NOT_FOUND, "ORG_UNIT_NOT_FOUND", "未找到机构"));
    }

    private void requireVersion(Integer version) {
        if (version == null || version < 1) {
            badRequest("VERSION_REQUIRED", "缺少有效的版本号");
        }
    }

    private List<OrgUnitDtos.Option> options(String[][] values) {
        return java.util.Arrays.stream(values)
                .map(value -> new OrgUnitDtos.Option(value[0], value[1])).toList();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("操作日志序列化失败", exception);
        }
    }

    private static String requireText(String value, String label) {
        String result = trimToNull(value);
        if (result == null) {
            throw new OrgUnitBusinessException(
                    HttpStatus.BAD_REQUEST, "REQUIRED_FIELD", label + "不能为空");
        }
        return result;
    }

    private static String upper(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(java.util.Locale.ROOT);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void badRequest(String code, String message) {
        throw new OrgUnitBusinessException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static void conflict(String code, String message) {
        throw new OrgUnitBusinessException(HttpStatus.CONFLICT, code, message);
    }

    private static final class TreeBuilder {
        private final UnitRow row;
        private final List<TreeBuilder> children = new ArrayList<>();

        private TreeBuilder(UnitRow row) {
            this.row = row;
        }

        private OrgUnitDtos.TreeNode toDto() {
            return new OrgUnitDtos.TreeNode(
                    row.id(), row.parentId(), row.unitCode(), row.unitName(), row.unitType(),
                    row.status(), row.verificationStatus(),
                    children.stream().map(TreeBuilder::toDto).toList());
        }
    }
}
