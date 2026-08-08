package com.niet.facultyachievement.dto.dashboard;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryStatDTO {
    private String categoryName;
    private Long count;
}
