package com.bank.platform.mcp.expert.codereview;

import java.util.List;

/**
 * The tool-specific task for {@code code_review_expert} (Part 4.1 input). The shared
 * envelope ({@code repo}, {@code options}, {@code budget}) lives on
 * {@link com.bank.platform.mcp.contract.ExpertRequest}; this record is just the
 * {@code task} payload, deserialized from the generic map by the framework.
 *
 * @param diff          the unified git diff under review (the primary evidence)
 * @param changedFiles  optional full after-image contents, to resolve citations
 *                      beyond the diff hunks
 * @param reviewProfile which defect categories to emphasize
 * @param standards     bank coding standards to enforce, by id
 */
public record CodeReviewTask(
        String diff,
        List<ChangedFile> changedFiles,
        ReviewProfile reviewProfile,
        List<String> standards
) {
    public CodeReviewTask {
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        standards = standards == null ? List.of() : List.copyOf(standards);
        reviewProfile = reviewProfile == null ? ReviewProfile.DEFAULT : reviewProfile;
    }

    /** Full post-change content of a changed file, used to resolve citations off-hunk. */
    public record ChangedFile(String path, String afterContent) {}

    /** Raises/lowers category emphasis in the prompt (Part 4.1). */
    public enum ReviewProfile { DEFAULT, SECURITY, CONCURRENCY, PERFORMANCE }
}
