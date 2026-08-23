package com.CemHarput.IncidentInvestigator.incident.application;

import com.CemHarput.IncidentInvestigator.incident.api.CreateIncidentRequest;
import com.CemHarput.IncidentInvestigator.incident.api.AddRootCauseRequest;
import com.CemHarput.IncidentInvestigator.incident.api.IncidentResponse;
import com.CemHarput.IncidentInvestigator.incident.api.RootCauseResponse;
import com.CemHarput.IncidentInvestigator.incident.domain.Incident;
import com.CemHarput.IncidentInvestigator.incident.domain.RootCause;
import com.CemHarput.IncidentInvestigator.incident.exception.IncidentNotFoundException;
import com.CemHarput.IncidentInvestigator.incident.infrastructure.IncidentRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IncidentService {

    private final IncidentRepository incidentRepository;

    public IncidentService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    public IncidentResponse createIncident(CreateIncidentRequest request) {
        Incident incident = new Incident(
                request.title(),
                request.description(),
                request.incidentType(),
                request.source(),
                request.occurredAt()
        );

        Incident savedIncident = incidentRepository.save(incident);
        return toResponse(savedIncident);
    }

    @Transactional(readOnly = true)
    public IncidentResponse getIncident(Long id) {
        return toResponse(findIncident(id));
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> getAllIncidents() {
        return incidentRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public IncidentResponse startInvestigation(Long id) {
        Incident incident = findIncident(id);
        incident.startInvestigation();
        return toResponse(incident);
    }

    public IncidentResponse addRootCause(Long id, AddRootCauseRequest request) {
        Incident incident = findIncident(id);
        RootCause rootCause = new RootCause(request.summary(), request.rootCauseType(), request.confirmed());
        incident.identifyRootCause(rootCause);
        return toResponse(incident);
    }

    public IncidentResponse resolveIncident(Long id) {
        Incident incident = findIncident(id);
        incident.resolve();
        return toResponse(incident);
    }

    public IncidentResponse closeIncident(Long id) {
        Incident incident = findIncident(id);
        incident.close();
        return toResponse(incident);
    }

    private Incident findIncident(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new IncidentNotFoundException(id));
    }
    // we do not need seperate mapper class for the currency state of the project
    private IncidentResponse toResponse(Incident incident) {
        RootCauseResponse rootCauseResponse = null;
        if (incident.getRootCause() != null) {
            RootCause rootCause = incident.getRootCause();
            rootCauseResponse = new RootCauseResponse(
                    rootCause.getId(),
                    rootCause.getSummary(),
                    rootCause.getRootCauseType(),
                    rootCause.isConfirmed(),
                    rootCause.getCreatedAt()
            );
        }

        return new IncidentResponse(
                incident.getId(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getIncidentType(),
                incident.getSource(),
                incident.getAssignedTo(),
                incident.getStatus(),
                incident.getReportedAt(),
                incident.getOccurredAt(),
                incident.getResolvedAt(),
                incident.getCreatedAt(),
                incident.getUpdatedAt(),
                rootCauseResponse
        );
    }
}
