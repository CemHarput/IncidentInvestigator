package com.CemHarput.IncidentInvestigator.incident.api;

import com.CemHarput.IncidentInvestigator.incident.application.IncidentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping
    public ResponseEntity<IncidentResponse> createIncident(@Valid @RequestBody CreateIncidentRequest request) {
        IncidentResponse response = incidentService.createIncident(request);
        return ResponseEntity
                .created(URI.create("/api/v1/incidents/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncidentResponse> getIncident(@PathVariable Long id) {
        return ResponseEntity.ok(incidentService.getIncident(id));
    }

    @GetMapping
    public ResponseEntity<List<IncidentResponse>> getAllIncidents() {
        return ResponseEntity.ok(incidentService.getAllIncidents());
    }

    @PostMapping("/{id}/investigation")
    public IncidentResponse startInvestigation(@PathVariable Long id) {
        return incidentService.startInvestigation(id);
    }
}
