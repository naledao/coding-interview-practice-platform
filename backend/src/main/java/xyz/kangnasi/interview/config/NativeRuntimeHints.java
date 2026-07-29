package xyz.kangnasi.interview.config;

import org.springaicommunity.mcp.context.DefaultMetaProvider;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import xyz.kangnasi.interview.statistics.TagStatisticsAggregation;

public final class NativeRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(
                TagStatisticsAggregation.class,
                MemberCategory.INTROSPECT_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS
        );
        hints.reflection().registerType(
                DefaultMetaProvider.class,
                MemberCategory.INTROSPECT_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS
        );
    }
}
