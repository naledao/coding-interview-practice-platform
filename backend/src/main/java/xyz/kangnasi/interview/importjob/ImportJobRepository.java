package xyz.kangnasi.interview.importjob;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {

    Optional<ImportJob> findFirstByDocumentIdOrderByCreatedAtDesc(Long documentId);

    List<ImportJob> findByDocumentIdIn(Iterable<Long> documentIds);

    Page<ImportJob> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ImportJob> findByStatus(ImportJobStatus status, Pageable pageable);

    @Query("""
            select job
            from ImportJob job
            where (:status is null or job.status = :status)
              and (:documentName is null or lower(job.documentName) like lower(concat('%', :documentName, '%')))
              and (:createdFrom is null or job.createdAt >= :createdFrom)
              and (:createdTo is null or job.createdAt <= :createdTo)
            """)
    Page<ImportJob> search(
            @Param("status") ImportJobStatus status,
            @Param("documentName") String documentName,
            @Param("createdFrom") Instant createdFrom,
            @Param("createdTo") Instant createdTo,
            Pageable pageable
    );
}
