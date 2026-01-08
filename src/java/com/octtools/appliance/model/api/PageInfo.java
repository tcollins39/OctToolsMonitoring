package com.octtools.appliance.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageInfo {
    private Integer totalCount;      // API spec shows this field
    private boolean hasNextPage;     // API spec shows this field  
    private String endCursor;        // Base64 encoded cursor for next page
}
