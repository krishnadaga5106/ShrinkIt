package com.Inhuman.shrinkit.controllers;


import com.Inhuman.shrinkit.dtos.ClickEventDto;
import com.Inhuman.shrinkit.dtos.UrlDto;
import com.Inhuman.shrinkit.models.User;
import com.Inhuman.shrinkit.services.UrlService;
import com.Inhuman.shrinkit.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/urls")
public class UrlController {

    private final UrlService urlService;
    private final UserService userService;

    @PostMapping("/short")
    public ResponseEntity<UrlDto> shortUrl(@RequestBody Map<String, String> request, Principal principal){

        String originalUrl = request.get("originalUrl");
        User user = userService.getByUsername(principal.getName());

        UrlDto urlDto = urlService.shorten(originalUrl, user);
        return ResponseEntity.ok(urlDto);
    }

    @GetMapping("/getAll")
    public ResponseEntity<List<UrlDto>> getAll(Principal principal){
        User user = userService.getByUsername(principal.getName());
        return ResponseEntity.ok(urlService.getAllUrls(user));
    }


    //gets all the clicks between dates, returns by date and count
    @GetMapping("/dated-analytics/{shortUrl}")
    public ResponseEntity<List<ClickEventDto>> getAnalyticsBtwDates(@PathVariable String shortUrl,
                                                            @RequestParam String startDate,
                                                            @RequestParam String endDate){

        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        LocalDateTime start = LocalDateTime.parse(startDate, formatter);
        LocalDateTime end = LocalDateTime.parse(endDate, formatter);

        return ResponseEntity.ok(urlService.getClicksBtwDate(shortUrl, start, end));
    }


    //gets all the clicks, it gets it by date and count
    @GetMapping("/analytics/{shortUrl}")
    public ResponseEntity<List<ClickEventDto>> getAnalytics(@PathVariable String shortUrl){
        return ResponseEntity.ok(urlService.getClicks(shortUrl));
    }

}
