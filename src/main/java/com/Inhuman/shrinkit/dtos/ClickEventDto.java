package com.Inhuman.shrinkit.dtos;

import lombok.Data;
import java.time.LocalDate;

@Data
public class ClickEventDto {
    private LocalDate clickDate;
    private int clickCount;
}
