package com.octtools.appliance.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DrainResponse {
    private Long drainId;                    // API returns Long
    private String estimatedTimeToDrain;     // ISO-8601 duration format
}
