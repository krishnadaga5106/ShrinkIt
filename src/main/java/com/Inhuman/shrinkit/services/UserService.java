package com.Inhuman.shrinkit.services;

import com.Inhuman.shrinkit.dtos.LoginRequest;
import com.Inhuman.shrinkit.models.User;
import com.Inhuman.shrinkit.repos.UserRepo;
import com.Inhuman.shrinkit.security.jwt.JwtAuthResponse;
import com.Inhuman.shrinkit.security.jwt.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepo userRepo;
    private final AuthenticationManager authManager;
    private final JwtUtils  jwtUtils;


    public JwtAuthResponse login(LoginRequest loginRequest){
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(), loginRequest.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(auth);

        UserDetailImpl userDetail = (UserDetailImpl) auth.getPrincipal();
        String jwtToken = jwtUtils.generateToken(userDetail);

        return new JwtAuthResponse(jwtToken);
    }


    public User register(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepo.save(user);
    }

    public User getByUsername(String name) {
        return userRepo.findByUsername(name).orElseThrow();
    }
}
