package com.zhineng.platform.basicinfo.orgunit.dto;

import java.util.List;

public final class OrgUnitDtos {
    private OrgUnitDtos() {
    }

    public record Option(String code, String label) {
    }

    public record Options(
            List<Option> unitTypes,
            List<Option> unitLevels,
            List<Option> organizationNatures,
            List<Option> statuses,
            List<Option> verificationStatuses
    ) {
    }

    public record TreeNode(
            long id,
            Long parentId,
            String unitCode,
            String unitName,
            String unitType,
            String status,
            String verificationStatus,
            List<TreeNode> children
    ) {
    }

    public record ListItem(
            long id,
            Long parentId,
            String parentName,
            String unitCode,
            String unitName,
            String unitShortName,
            String unitType,
            String unitLevel,
            String organizationNature,
            Integer approvedStaffing,
            int sortOrder,
            String status,
            String verificationStatus,
            int versionNo
    ) {
    }

    public record Page(
            List<ListItem> items,
            long total,
            int page,
            int size,
            int totalPages
    ) {
    }

    public record Stats(long totalUnits, long administrativeUnits, long publicInstitutions) {
    }

    public record Verification(
            long id,
            String result,
            String opinion,
            Long verifierId,
            String verifierName,
            String verifiedAt
    ) {
    }

    public record Detail(
            long id,
            Long parentId,
            String parentName,
            String unitCode,
            String unitName,
            String unitShortName,
            String unitType,
            String unitLevel,
            String organizationNature,
            Integer approvedStaffing,
            int sortOrder,
            String status,
            String verificationStatus,
            String verificationOpinion,
            Long createdBy,
            String createdByName,
            String createdAt,
            Long updatedBy,
            String updatedByName,
            String updatedAt,
            Long verifiedBy,
            String verifiedByName,
            String verifiedAt,
            int versionNo,
            long childCount,
            List<Verification> verificationHistory
    ) {
    }

    public record SaveRequest(
            Long parentId,
            String unitCode,
            String unitName,
            String unitShortName,
            String unitType,
            String unitLevel,
            String organizationNature,
            Integer approvedStaffing,
            Integer sortOrder,
            Integer versionNo
    ) {
    }

    public record StatusRequest(String status, Integer versionNo) {
    }

    public record VerificationRequest(String result, String opinion, Integer versionNo) {
    }

    public record Error(String code, String message) {
    }
}
