package com.bootcamp.paymentdemo.domain.user.repository;

import com.bootcamp.paymentdemo.domain.user.entity.UserEntity2;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository2 extends JpaRepository<UserEntity2, Long> {
    Optional<Object> findByIdWithLock(Long userId);
}
