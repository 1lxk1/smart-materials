package com.smartmaterials.common;

/** 业务异常：携带 HTTP 状态码与可直接展示给前端的提示。 */
public class BizException extends RuntimeException {

    private final int status;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static BizException notFound(String message) {
        return new BizException(404, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(403, message);
    }
}
