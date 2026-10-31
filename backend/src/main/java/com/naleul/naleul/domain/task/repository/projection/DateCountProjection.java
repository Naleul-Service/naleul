package com.naleul.naleul.domain.task.repository.projection;

import java.time.LocalDate;

public interface DateCountProjection {
    LocalDate getTaskDate();
    Long getCount();
}