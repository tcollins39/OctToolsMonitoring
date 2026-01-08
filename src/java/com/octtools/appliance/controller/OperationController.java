package com.octtools.appliance.controller;

import com.octtools.appliance.model.Operation;
import com.octtools.appliance.repository.OperationRepository;
import com.octtools.appliance.service.RemediationQueue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
public class OperationController {
    
    private final OperationRepository operationRepository;

    public OperationController(OperationRepository operationRepository) {
        this.operationRepository = operationRepository;
    }

    @GetMapping("/operations")
    public Page<Operation> getAllOperations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String applianceId) {
        
        Pageable pageable = PageRequest.of(page, size);
        
        if (applianceId != null && !applianceId.trim().isEmpty()) {
            return operationRepository.findByApplianceIdOrderByProcessedAtDesc(applianceId.trim(), pageable);
        } else {
            return operationRepository.findAllByOrderByProcessedAtDesc(pageable);
        }
    }

    @GetMapping("/operations/{id}")
    public ResponseEntity<Operation> getOperation(@PathVariable Long id) {
        Optional<Operation> operation = operationRepository.findById(id);
        return operation.map(ResponseEntity::ok)
                      .orElse(ResponseEntity.notFound().build());
    }
}
