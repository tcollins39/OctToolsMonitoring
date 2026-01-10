package com.octtools.appliance.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "operations")
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Operation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String applianceId;
    private String operationType;
    private Instant processedAt;
    private String drainId;
    private String estimatedTimeToDrain;
    private String remediationId;
    private String remediationResult;
}
