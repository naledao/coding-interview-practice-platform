package xyz.kangnasi.interview.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import xyz.kangnasi.interview.auth.LoginLockedException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException exception) {
        HttpStatus status = exception.getStatus();
        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(status.value(), exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody() {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), "请求参数错误"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), exception.getMessage()));
    }

    @ExceptionHandler(LoginLockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleLoginLocked(LoginLockedException exception) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(HttpStatus.TOO_MANY_REQUESTS.value(), exception.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound() {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(HttpStatus.NOT_FOUND.value(), "资源不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException() {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务器内部错误"));
    }
}
