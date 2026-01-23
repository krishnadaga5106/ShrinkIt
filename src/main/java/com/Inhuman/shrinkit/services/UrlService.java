package com.Inhuman.shrinkit.services;

import com.Inhuman.shrinkit.dtos.ClickEventDto;
import com.Inhuman.shrinkit.dtos.UrlDto;
import com.Inhuman.shrinkit.models.ClickEvent;
import com.Inhuman.shrinkit.models.UrlMapping;
import com.Inhuman.shrinkit.models.User;
import com.Inhuman.shrinkit.repos.ClickEventRepo;
import com.Inhuman.shrinkit.repos.UrlMappingRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UrlService {

    @Value("${url.length}")
    private int len;

    @Value("${url.charSet}")
    private String charSet;

    private final UrlMappingRepo urlMappingRepo;
    private final ClickEventRepo clickEventRepo;

    private UrlDto convertToDto(UrlMapping urlMapping) {
        UrlDto urlDto = new UrlDto();
        urlDto.setShortUrl(urlMapping.getShortUrl());
        urlDto.setCreatedAt(urlMapping.getCreatedAt());
        urlDto.setOriginalUrl(urlMapping.getOriginal());
        urlDto.setId(urlMapping.getId());
        urlDto.setUsername(urlMapping.getUser().getUsername());
        urlDto.setClickCount(urlMapping.getClicks());

        return urlDto;
    }

    private List<ClickEventDto> convertToDto(List<ClickEvent> clickEvents) {
        Map<LocalDate, Integer> map = new HashMap<>();
        for(ClickEvent click : clickEvents){
            //get the click date
            LocalDate date = click.getDate().toLocalDate();
            //put the click date into the map if present, increase the count if not then put a new one
            map.put(date, map.getOrDefault(date, 0) + 1);
        }

        //for each entry in the map, make a new DTO obj and insert the info to form a list
        Set<LocalDate> keys = map.keySet();
        List<ClickEventDto> dtos = new ArrayList<>();
        for(LocalDate date : keys){
            ClickEventDto dto = new ClickEventDto();
            dto.setClickDate(date);
            dto.setClickCount(map.get(date));
            dtos.add(dto);
        }
        return dtos;
    }

    private String generate() {
        StringBuilder shortUrl = new StringBuilder();
        Random rand = new Random();
        for (int i = 0; i < len; i++) {
            shortUrl.append(charSet.charAt(rand.nextInt(charSet.length())));
        }
        return shortUrl.toString();
    }


    public UrlDto shorten(String originalUrl, User user) {
        String shortUrl = generate();

        while(urlMappingRepo.existsByShortUrl(shortUrl)){
            shortUrl = generate();
        }

        UrlMapping urlMapping = new UrlMapping();

        urlMapping.setOriginal(originalUrl);
        urlMapping.setShortUrl(shortUrl);
        urlMapping.setUser(user);
        urlMapping.setCreatedAt(LocalDateTime.now());
        urlMapping.setClicks(0);

        urlMappingRepo.save(urlMapping);

        return convertToDto(urlMapping);
    }

    public List<UrlDto> getAllUrls(User user) {
        List<UrlMapping> mappings = urlMappingRepo.findAllByUser(user);
        List<UrlDto> urlDtos = new ArrayList<>();

        for (UrlMapping mapping : mappings) {
            urlDtos.add(convertToDto(mapping));
        }
        return urlDtos;
    }

    //gets all the clicks between dates, returns by date and count
    public List<ClickEventDto> getClicksBtwDate(String shortUrl, LocalDateTime start, LocalDateTime end) {
        UrlMapping urlMapping = urlMappingRepo.findByShortUrl(shortUrl);
        //invalid short url
        if(urlMapping == null){
            return null;
        }
        return convertToDto(clickEventRepo.findAllByUrlMappingAndDateBetween(urlMapping, start, end));
    }

    //gets all the clicks, it gets it by date and count
    public List<ClickEventDto> getClicks(String shortUrl) {
        UrlMapping urlMapping = urlMappingRepo.findByShortUrl(shortUrl);
        if(urlMapping == null){
            return null;
        }
        return convertToDto(urlMapping.getClickEvents());
    }

    public UrlMapping redirect(String shortUrl) {
        UrlMapping url = urlMappingRepo.findByShortUrl(shortUrl);

        if(url == null){
            return null;
        }

        url.setClicks(url.getClicks() + 1);
        urlMappingRepo.save(url);

        ClickEvent click = new  ClickEvent();
        click.setDate(LocalDateTime.now());
        click.setUrlMapping(url);
        clickEventRepo.save(click);

        return url;
    }

}
