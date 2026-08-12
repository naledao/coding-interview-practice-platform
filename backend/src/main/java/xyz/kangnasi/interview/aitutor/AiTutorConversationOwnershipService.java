package xyz.kangnasi.interview.aitutor;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.kangnasi.interview.common.AppException;

@Service
public class AiTutorConversationOwnershipService {

    private final AiTutorConversationBindingRepository repository;

    public AiTutorConversationOwnershipService(AiTutorConversationBindingRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void bind(Long userId, String conversationId) {
        requireUserId(userId);
        String normalizedId = normalizeUuid(conversationId, "conversationId");
        AiTutorConversationBinding existing = repository.findByConversationId(normalizedId).orElse(null);
        if (existing != null) {
            requireSameUser(userId, existing);
            existing.touch();
            return;
        }
        repository.save(AiTutorConversationBinding.create(userId, normalizedId));
    }

    @Transactional
    public String requireOwned(Long userId, String conversationId) {
        requireUserId(userId);
        String normalizedId = normalizeUuid(conversationId, "conversationId");
        AiTutorConversationBinding binding = repository.findByConversationId(normalizedId)
                .orElseThrow(() -> AppException.notFound("AI 助教会话不存在"));
        requireSameUser(userId, binding);
        binding.touch();
        return normalizedId;
    }

    public static String normalizeUuid(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw AppException.badRequest(fieldName + " 不能为空");
        }
        try {
            String normalized = UUID.fromString(value.trim()).toString();
            if (!normalized.equalsIgnoreCase(value.trim())) {
                throw new IllegalArgumentException("UUID 格式不标准");
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw AppException.badRequest(fieldName + " 必须是有效 UUID");
        }
    }

    private void requireSameUser(Long userId, AiTutorConversationBinding binding) {
        if (!userId.equals(binding.getUserId())) {
            throw AppException.notFound("AI 助教会话不存在");
        }
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw AppException.unauthorized("未登录或登录已过期");
        }
    }
}
