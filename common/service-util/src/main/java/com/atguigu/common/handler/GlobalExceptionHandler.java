package com.atguigu.common.handler;

import com.atguigu.common.result.Result;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    //全局异常处理
//    @ExceptionHandler(Exception.class)
//    public Result error(){
//        return Result.fail().message("执行全局异常处理...");
//    }
//    @ExceptionHandler(ArithmeticException.class)
//    public Result error(ArithmeticException e){
//        e.printStackTrace();
//        return Result.fail().message("执行了特定异常处理");
//    }
}
