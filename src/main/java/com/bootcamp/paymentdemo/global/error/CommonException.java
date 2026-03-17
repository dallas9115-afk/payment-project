package com.bootcamp.paymentdemo.global.error;

import lombok.Getter;
import org.apache.tomcat.util.http.parser.Cookie;

@Getter
public class CommonException extends RuntimeException {
    private final CommonError commonError;

    public CommonException(CommonError commonError) {
        super(commonError.getMessage());
        this.commonError = commonError;
    }

}
