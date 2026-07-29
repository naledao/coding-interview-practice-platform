package xyz.kangnasi.interview.common;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
        List<T> items,
        int page,
        int pageSize,
        long total
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalElements()
        );
    }
}
