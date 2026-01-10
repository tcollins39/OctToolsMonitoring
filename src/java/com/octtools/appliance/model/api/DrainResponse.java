package com.octtools.appliance.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DrainResponse {
    private String drainId;                  // API returns UUID string
    private String estimatedTimeToDrain;     // ISO-8601 duration format
}
