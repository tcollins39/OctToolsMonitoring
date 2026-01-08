package com.octtools.appliance.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Appliance {
    private String id;
    
    @JsonProperty("opStatus")
    private String opStatus;
    
    @JsonProperty("lastHeardFromOn")
    private String lastHeardFromOn;  // ISO-8601 string as per API spec
}
