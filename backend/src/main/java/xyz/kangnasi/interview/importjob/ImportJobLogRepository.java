package xyz.kangnasi.interview.importjob;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobLogRepository extends JpaRepository<ImportJobLog, Long> {

    List<ImportJobLog> findByImportJobIdOrderByCreatedAtAsc(Long importJobId);
}
