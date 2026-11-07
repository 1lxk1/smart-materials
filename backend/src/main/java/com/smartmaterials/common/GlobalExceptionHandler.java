package com.smartmaterials.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** 全局异常处理：统一返回 {timestamp,status,message,path}，前端可直接提示。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Map<String, Object>> handleBiz(BizException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex,
                                                                HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("；"));
        return build(400, message.isBlank() ? "参数校验失败" : message, request.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(403, "没有权限执行该操作", request.getRequestURI());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoHandlerFoundException ex,
                                                              HttpServletRequest request) {
        return build(404, "接口不存在", request.getRequestURI());
    }

    /** 未匹配到任何 Controller 时 Spring 会落到静态资源处理器，这里统一转成 404。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex,
                                                                HttpServletRequest request) {
        return build(404, "接口不存在: " + request.getMethod() + " " + request.getRequestURI(),
                request.getRequestURI());
    }

    /** 请求体不是合法 JSON（例如前端漏传 Content-Type 或写错引号）：返回 400 而不是 500。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex,
                                                                HttpServletRequest request) {
        log.warn("请求体解析失败: {} {}", request.getMethod(), request.getRequestURI());
        return build(400, "请求体不是合法 JSON，请检查 Content-Type 与 JSON 格式", request.getRequestURI());
    }

    /** 路径变量 / 查询参数类型不匹配（如 /api/batches/abc 期望数字）。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                                  HttpServletRequest request) {
        return build(400, "参数 " + ex.getName() + " 类型不正确", request.getRequestURI());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex,
                                                                  HttpServletRequest request) {
        return build(400, "缺少必填参数：" + ex.getParameterName(), request.getRequestURI());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                        HttpServletRequest request) {
        return build(405, "接口不支持 " + ex.getMethod() + " 方法", request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex, HttpServletRequest request) {
        log.error("未处理异常: {}", request.getRequestURI(), ex);
        return build(500, "服务器内部错误: " + ex.getMessage(), request.getRequestURI());
    }

    private ResponseEntity<Map<String, Object>> build(int status, String message, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().format(FMT));
        body.put("status", status);
        body.put("message", message);
        body.put("path", path);
        return ResponseEntity.status(HttpStatus.valueOf(status)).body(body);
    }
}
