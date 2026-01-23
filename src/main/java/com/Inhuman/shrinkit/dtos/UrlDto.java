package com.Inhuman.shrinkit.dtos;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UrlDto {
    private long id;
    private String originalUrl;
    private String shortUrl;
    private int clickCount;
    private LocalDateTime createdAt;
    private String username;
}
