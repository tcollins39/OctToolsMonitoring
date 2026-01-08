package com.octtools.appliance.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemediateResponse {
    private Long remediationId;              // API returns Long
    private String remediationResult;        // "SUCCESS" or other status
}
