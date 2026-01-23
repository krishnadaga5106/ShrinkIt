package com.Inhuman.shrinkit.dtos;

import lombok.Data;

import java.util.List;

@Data
public class RegisterRequest {
    private String username;
    private String password;
    private String email;
    private List<String> roles;
}
