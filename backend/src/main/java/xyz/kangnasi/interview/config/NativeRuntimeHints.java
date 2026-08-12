package xyz.kangnasi.interview.config;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import org.springaicommunity.mcp.context.DefaultMetaProvider;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import xyz.kangnasi.interview.aitutor.AiTutorModelCatalog;
import xyz.kangnasi.interview.aitutor.AiTutorModelOption;
import xyz.kangnasi.interview.aitutor.AiTutorRunAccepted;
import xyz.kangnasi.interview.aitutor.AiTutorRunCancelResult;
import xyz.kangnasi.interview.aitutor.AiTutorRunRequest;
import xyz.kangnasi.interview.aitutor.CodexRunContext;
import xyz.kangnasi.interview.aitutor.CodexRunCreateRequest;
import xyz.kangnasi.interview.aitutor.CodexRunInput;
import xyz.kangnasi.interview.aitutor.CodexRunOptions;
import xyz.kangnasi.interview.aitutor.CodexRunReference;
import xyz.kangnasi.interview.email.EmailAcceptedResponse;
import xyz.kangnasi.interview.email.EmailSendRequest;
import xyz.kangnasi.interview.statistics.TagStatisticsAggregation;

public final class NativeRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.resources().registerPattern("prompts/codex-question-generation.md");
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
        for (Class<?> type : new Class<?>[]{ServiceInfo.class, Instance.class}) {
            hints.reflection().registerType(
                    type,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_PUBLIC_METHODS
            );
        }
        for (Class<?> type : new Class<?>[]{
                AiTutorModelCatalog.class,
                AiTutorModelOption.class,
                AiTutorRunAccepted.class,
                AiTutorRunCancelResult.class,
                AiTutorRunRequest.class,
                CodexRunContext.class,
                CodexRunCreateRequest.class,
                CodexRunInput.class,
                CodexRunOptions.class,
                CodexRunReference.class,
                EmailAcceptedResponse.class,
                EmailSendRequest.class
        }) {
            hints.reflection().registerType(
                    type,
                    MemberCategory.INTROSPECT_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_PUBLIC_METHODS
            );
        }
    }
}
