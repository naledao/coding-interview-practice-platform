package xyz.kangnasi.interview.aitutor;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiTutorConversationBindingRepository
        extends JpaRepository<AiTutorConversationBinding, Long> {

    Optional<AiTutorConversationBinding> findByConversationId(String conversationId);
}
