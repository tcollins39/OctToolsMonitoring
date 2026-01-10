package com.octtools.appliance.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemediateResponse {
    private String remediationId;            // API returns String UUID
    private String remediationResult;        // "SUCCESS" or other status
}
