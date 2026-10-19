package com.example.demo.common;

import lombok.Data;

@Data
public class Result<T> {

    private int code;
    private String message;
    private T object;

    public Result() {
    }

    // 构造方法：传入数据时，保存数据
    public Result(T object) {
        this.object = object;
    }


    // 成功：不携带数据
    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("操作成功");
        return result;
    }

    // 成功：携带数据
    public static <T> Result<T> success(T object) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("操作成功");
        result.setObject(object);
        return result;
    }

    // 失败：默认错误
    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.setCode(400);
        result.setMessage(message);
        return result;
    }

    // 失败：自定义错误码
    public static <T> Result<T> error(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
