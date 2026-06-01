package com.pm.auth_service.service;

import com.pm.auth_service.dto.LoginRequestDTO;
import com.pm.auth_service.model.User;
import com.pm.auth_service.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

public class AuthService {

    public final UserService userService;
    public final PasswordEncoder passwordEncoder;
    public final JwtUtil jwtUtil;

    public AuthService(UserService userService, PasswordEncoder passwordEncoder, JwtUtil jwtUtil){
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public Optional<String> authenticate(LoginRequestDTO loginRequestDTO) {
    Optional<String> token = userService
            .findByEmail(loginRequestDTO.getEmail())
            .filter(u -> passwordEncoder.
                    matches(loginRequestDTO.getPassword(),u.getPassword()))
            .map(u -> jwtUtil.generateToken(u.getEmail(),u.getRole()));
    return token;
}

}

