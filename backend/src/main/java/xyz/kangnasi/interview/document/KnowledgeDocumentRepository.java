package xyz.kangnasi.interview.document;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    List<KnowledgeDocument> findByUploadIdOrderByIdAsc(Long uploadId);

    Page<KnowledgeDocument> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
