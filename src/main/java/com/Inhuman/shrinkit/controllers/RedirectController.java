package com.Inhuman.shrinkit.controllers;

import com.Inhuman.shrinkit.models.UrlMapping;
import com.Inhuman.shrinkit.services.UrlService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final UrlService urlService;

    @GetMapping("/{shortUrl}")
    public void redirect(@PathVariable String shortUrl, HttpServletResponse response) throws IOException {
        UrlMapping url = urlService.redirect(shortUrl);

        if(url == null){
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.sendRedirect(url.getOriginal());
    }

}
