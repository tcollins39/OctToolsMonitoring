package com.octtools.appliance.model.api;

import com.octtools.appliance.model.Appliance;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppliancePageResponse {
    private List<Appliance> data;
    private PageInfo pageInfo;
}
