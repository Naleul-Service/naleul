package com.naleul.naleul.domain.goalCreation.dto.slot;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;

public record TimeRange(
        @JsonFormat(pattern = "HH:mm") LocalTime start,
        @JsonFormat(pattern = "HH:mm") LocalTime end
) {
    public boolean contains(LocalTime time) {
        return time != null && !time.isBefore(start) && !time.isAfter(end);
    }
}
