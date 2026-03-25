package com.bootcamp.paymentdemo.config;

import lombok.Getter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collections;

@Getter
public class CustomUser extends User {
    private final Long id; // DB의 PK (Long 타입 customerId)
    private final String email;

    public CustomUser(Long id, String email, String password) {
        // 부모 클래스(User)에 아이디, 패스워드, 권한을 넘겨줍니다.
        super(email, password, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        this.id = id;
        this.email = email;
    }
}
