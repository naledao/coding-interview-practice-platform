package xyz.kangnasi.interview.document;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import xyz.kangnasi.interview.auth.UserPrincipal;
import xyz.kangnasi.interview.common.ApiResponse;
import xyz.kangnasi.interview.common.PageResponse;
import xyz.kangnasi.interview.importjob.ImportJobCreateResponse;

@RestController
@RequestMapping("/api/admin")
public class AdminDocumentController {

    private final DocumentService documentService;

    public AdminDocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/documents")
    public ApiResponse<DocumentUploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean autoStart,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(documentService.upload(file, autoStart, principal));
    }

    @GetMapping("/document-uploads/{uploadId}")
    public ApiResponse<UploadDetailResponse> uploadDetail(@PathVariable Long uploadId) {
        return ApiResponse.ok(documentService.uploadDetail(uploadId));
    }

    @GetMapping("/documents")
    public ApiResponse<PageResponse<DocumentListItemResponse>> listDocuments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(PageResponse.from(documentService.listDocuments(page, pageSize)));
    }

    @GetMapping("/documents/{documentId}")
    public ApiResponse<DocumentDetailResponse> documentDetail(@PathVariable Long documentId) {
        return ApiResponse.ok(documentService.documentDetail(documentId));
    }

    @PostMapping("/documents/{documentId}/import-jobs")
    public ApiResponse<ImportJobCreateResponse> createImportJob(@PathVariable Long documentId) {
        return ApiResponse.ok(ImportJobCreateResponse.from(documentService.createImportJob(documentId, true)));
    }
}
