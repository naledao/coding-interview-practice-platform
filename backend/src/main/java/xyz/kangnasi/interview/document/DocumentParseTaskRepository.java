package xyz.kangnasi.interview.document;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentParseTaskRepository extends JpaRepository<DocumentParseTask, Long> {

    Optional<DocumentParseTask> findFirstByUploadIdOrderByCreatedAtDesc(Long uploadId);
}
