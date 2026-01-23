package com.Inhuman.shrinkit.repos;

import com.Inhuman.shrinkit.models.UrlMapping;
import com.Inhuman.shrinkit.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UrlMappingRepo extends JpaRepository<UrlMapping, Long> {
    boolean existsByShortUrl(String shortUrl);
    List<UrlMapping> findAllByUser(User user);

    UrlMapping findByShortUrl(String shortUrl);
}
