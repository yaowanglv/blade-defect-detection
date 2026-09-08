package com.example.springb.exception;



import com.example.springb.common.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 全局异常捕获器
 */
@ControllerAdvice("com.example.springb.controller")
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MultipartException.class)
    @ResponseBody
    public Result multipartError(MultipartException e, HttpServletResponse response) {
        log.error("文件上传请求解析失败", e);
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return Result.error("400", "上传文件数量或大小超过限制，请减少图片数量后重试");
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody // 将result对象转换成 json的格式
    public Result error(Exception e) {
        log.error("系统异常", e);
        System.out.println("系统异常: " + e.getMessage()); // 打印错误信息

        return Result.error("系统异常");
    }

    @ExceptionHandler(CustomerException.class)
    @ResponseBody // 将result对象转换成 json的格式
    public Result customerError(CustomerException e, HttpServletResponse response) {
        log.error("自定义错误", e);
        System.out.println("自定义错误: " + e.getMsg()); // 打印错误信
        response.setStatus(resolveHttpStatus(e.getCode()));
        return Result.error(e.getCode(), e.getMsg());
    }

    private int resolveHttpStatus(String code) {
        if (code == null) {
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
        if (code.startsWith("401")) {
            return HttpServletResponse.SC_UNAUTHORIZED;
        }
        if (code.startsWith("403")) {
            return HttpServletResponse.SC_FORBIDDEN;
        }
        if (code.startsWith("400")) {
            return HttpServletResponse.SC_BAD_REQUEST;
        }
        return HttpServletResponse.SC_OK;
    }
}
