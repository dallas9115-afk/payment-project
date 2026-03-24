package com.bootcamp.paymentdemo.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequestDto(
        @NotBlank(message = "이름은 필수 입력값입니다.")
        String name,
        @NotBlank(message = "이메일은 필수 입력값입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,
        @NotBlank(message = "비밀번호는 필수 입력값입니다.")
        @Size(min = 8, max = 200, message = "비밀번호 길이는 8~200자 사이로 작성해주시기 바랍니다.")
        String password,
        @NotBlank(message = "전화번호는 필수입니다")
        @Pattern(
                regexp = "^\\d{3}-\\d{3,4}-\\d{4}$",
                message = "전화번호 형식이 잘못되었습니다.(예시: 010-0000-0000)"
        )
        String phoneNumber
) {
}