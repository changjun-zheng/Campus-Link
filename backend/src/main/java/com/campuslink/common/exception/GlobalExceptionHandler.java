package com.campuslink.common.exception;

import com.campuslink.common.result.ApiError;
import com.campuslink.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Set;

/**
 * 统一异常出口。日志分三档：业务错误（{@link ApiException}）是设计内流程，不记；
 * 框架级客户端错误记 WARN 且不打全栈；只有兜底分支记 ERROR + 全栈。
 * 否则客户端错误会污染 5xx 监控与错误日志。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException e) {
        ResultCode code = e.getCode();
        return ResponseEntity.status(code.getHttpStatus()).body(ApiError.of(code));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .orElse(ResultCode.INVALID_PARAM.getMessage());
        return clientError(ResultCode.INVALID_PARAM, detail, e);
    }

    /**
     * 请求体不可解析：畸形 JSON、缺失 body、非法字节序列。
     * 不回显 Jackson 原始消息——其中含内部类名与部分原始输入，对客户端无用且是信息泄露面。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException e) {
        return clientError(ResultCode.INVALID_PARAM, "请求体缺失或不是合法 JSON", e);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class})
    public ResponseEntity<ApiError> handleBadArgument(Exception e) {
        return clientError(ResultCode.INVALID_PARAM, ResultCode.INVALID_PARAM.getMessage(), e);
    }

    /**
     * 上传体积超限（CR-191）：multipart 解析在进入 Controller 之前就抛，故必须单独映射，
     * 否则会落进兜底分支变成 500。WARN 档、不打全栈（客户端可控错误不污染 5xx 监控）。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return clientError(ResultCode.FILE_TOO_LARGE, ResultCode.FILE_TOO_LARGE.getMessage(), e);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        ResponseEntity.BodyBuilder builder = responseBuilder(ResultCode.METHOD_NOT_ALLOWED, e);
        Set<HttpMethod> supported = e.getSupportedHttpMethods();
        if (supported != null) {
            builder.allow(supported.toArray(HttpMethod[]::new));
        }
        return builder.body(ApiError.of(ResultCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return clientError(ResultCode.UNSUPPORTED_MEDIA_TYPE, ResultCode.UNSUPPORTED_MEDIA_TYPE.getMessage(), e);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiError> handleNoHandler(Exception e) {
        return clientError(ResultCode.ENDPOINT_NOT_FOUND, ResultCode.ENDPOINT_NOT_FOUND.getMessage(), e);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        log.error("unhandled exception", e);
        return ResponseEntity.status(ResultCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiError.of(ResultCode.INTERNAL_ERROR));
    }

    private ResponseEntity<ApiError> clientError(ResultCode code, String message, Exception e) {
        return responseBuilder(code, e).body(ApiError.of(code, message));
    }

    private ResponseEntity.BodyBuilder responseBuilder(ResultCode code, Exception e) {
        log.warn("client error {} ({}): {}", code.getCode(), e.getClass().getSimpleName(), e.getMessage());
        return ResponseEntity.status(code.getHttpStatus());
    }
}
