package xyz.kangnasi.interview.codextool;

import xyz.kangnasi.interview.question.TagCategory;

public record RecommendedTagToolResponse(
        Long tagId,
        String name,
        String normalizedName,
        TagCategory category,
        int questionCount
) {
}
