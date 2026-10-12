package com.hmdp.config;

import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Web 层全局异常处理器
 * <p>
 * 拦截所有 Controller 抛出的 RuntimeException，统一封装为 {@link Result} 失败响应返回前端，
 * 同时记录 ERROR 级别日志便于排查问题。
 * </p>
 * <p>
 * 使用 {@link RestControllerAdvice} 注解使其对所有 Controller 生效，
 * 若后续需要处理更多异常类型（如 {@code IllegalArgumentException}、自定义业务异常等），
 * 可在本类中新增 {@link ExceptionHandler} 方法。
 * </p>
 */
@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    /**
     * 兜底处理 RuntimeException
     * <p>
     * 捕获所有未被其他 Handler 覆盖的运行时异常，记录完整堆栈后返回通用错误提示。
     * 前端收到 {@link Result#fail(String)} 后统一展示 "服务器异常"，避免把内部细节暴露给用户。
     * </p>
     *
     * @param e Controller 层抛出的运行时异常
     * @return 统一格式的失败响应
     */
    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error(e.toString(), e);
        return Result.fail("服务器异常");
    }
}