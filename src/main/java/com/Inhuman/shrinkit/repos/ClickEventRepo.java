package com.Inhuman.shrinkit.repos;

import com.Inhuman.shrinkit.models.ClickEvent;
import com.Inhuman.shrinkit.models.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ClickEventRepo extends JpaRepository<ClickEvent, Long> {
    List<ClickEvent> findAllByUrlMappingAndDateBetween(UrlMapping urlMapping, LocalDateTime dateAfter, LocalDateTime dateBefore);
}
