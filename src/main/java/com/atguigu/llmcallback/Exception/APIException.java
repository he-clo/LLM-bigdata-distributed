//package com.atguigu.llmcallback.Exception;
//
//import com.atguigu.llmcallback.Enum.state;
//import lombok.Getter;
//
//@Getter
//public class APIException extends RuntimeException {
//
//    private Integer code;
//
//    public APIException(String message, Integer code) {
//        super(message);
//        this.code = code;
//    }
//
//
//    public APIException(state enums) {
//        super(enums.getMsg());
//        this.code = enums.getResult();
//    }
//
//}