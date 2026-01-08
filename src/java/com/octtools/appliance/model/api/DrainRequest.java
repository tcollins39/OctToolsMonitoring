package com.octtools.appliance.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DrainRequest {
    private String reason;
    private String actor;
}
