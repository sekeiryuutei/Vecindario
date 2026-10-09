package com.codevam.vecindad.assemblies.adapter.in.web;

import com.codevam.vecindad.assemblies.application.AssemblyService;
import com.codevam.vecindad.assemblies.domain.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assemblies")
@Tag(name = "Asambleas (Administración)")
public class AdminAssemblyController {

    public record CreateAssemblyRequest(
            @NotBlank @Size(max = 200) String title,
            @NotNull AssemblyType type,
            @NotNull Instant scheduledAt,
            @Size(max = 200) String location,
            boolean isVirtual,
            @Size(max = 500) String meetingLink,
            @DecimalMin("0.01") BigDecimal requiredQuorumPercentage
    ) {}

    public record CheckInRequest(
            @NotNull UUID unitId,
            UUID personId,
            boolean isProxy,
            @Size(max = 150) String proxyName
    ) {}

    public record CreateTopicRequest(
            int orderNum,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            VotingType votingType,
            boolean requiresQualifiedMajority,
            List<String> options
    ) {}

    public record StatusRequest(
            @NotNull AssemblyStatus status
    ) {}

    private final AssemblyService service;

    public AdminAssemblyController(AssemblyService service) {
        this.service = service;
    }

    @Operation(summary = "Lista todas las asambleas")
    @GetMapping
    @PreAuthorize("hasAuthority('ASSEMBLIES_VIEW')")
    public List<Assembly> list() {
        return service.listAssemblies();
    }

    @Operation(summary = "Obtiene detalle de una asamblea")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ASSEMBLIES_VIEW')")
    public Assembly getById(@PathVariable UUID id) {
        return service.getAssembly(id);
    }

    @Operation(summary = "Programa una nueva asamblea general")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ASSEMBLIES_MANAGE')")
    public Assembly create(@Valid @RequestBody CreateAssemblyRequest r) {
        return service.createAssembly(new AssemblyService.CreateAssemblyCommand(
                r.title(), r.type(), r.scheduledAt(), r.location(),
                r.isVirtual(), r.meetingLink(), r.requiredQuorumPercentage()
        ));
    }

    @Operation(summary = "Cambia el estado de una asamblea (IN_PROGRESS, CLOSED, etc.)")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ASSEMBLIES_MANAGE')")
    public Assembly updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest r) {
        return service.updateStatus(id, r.status());
    }

    @Operation(summary = "Registra asistencia / quórum de un inmueble (presencial o por poder)")
    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasAuthority('ASSEMBLIES_MANAGE')")
    public QuorumSummary checkIn(@PathVariable UUID id, @Valid @RequestBody CheckInRequest r) {
        return service.registerAttendance(id, r.unitId(), r.personId(), r.isProxy(), r.proxyName());
    }

    @Operation(summary = "Consulta quórum actual de la asamblea")
    @GetMapping("/{id}/quorum")
    @PreAuthorize("hasAuthority('ASSEMBLIES_VIEW')")
    public QuorumSummary getQuorum(@PathVariable UUID id) {
        return service.getQuorum(id);
    }

    @Operation(summary = "Lista asistentes registrados")
    @GetMapping("/{id}/attendances")
    @PreAuthorize("hasAuthority('ASSEMBLIES_VIEW')")
    public List<AssemblyAttendance> listAttendances(@PathVariable UUID id) {
        return service.listAttendances(id);
    }

    @Operation(summary = "Crea un punto / tema de votación")
    @PostMapping("/{id}/topics")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ASSEMBLIES_MANAGE')")
    public AssemblyTopic createTopic(@PathVariable UUID id, @Valid @RequestBody CreateTopicRequest r) {
        return service.createTopic(id, new AssemblyService.CreateTopicCommand(
                r.orderNum(), r.title(), r.description(), r.votingType(),
                r.requiresQualifiedMajority(), r.options()
        ));
    }

    @Operation(summary = "Lista temas de la asamblea")
    @GetMapping("/{id}/topics")
    @PreAuthorize("hasAuthority('ASSEMBLIES_VIEW')")
    public List<AssemblyTopic> listTopics(@PathVariable UUID id) {
        return service.listTopics(id);
    }

    @Operation(summary = "Abre la votación para un tema")
    @PostMapping("/topics/{topicId}/open-vote")
    @PreAuthorize("hasAuthority('ASSEMBLIES_MANAGE')")
    public AssemblyTopic openVote(@PathVariable UUID topicId) {
        return service.openVote(topicId);
    }

    @Operation(summary = "Cierra la votación de un tema")
    @PostMapping("/topics/{topicId}/close-vote")
    @PreAuthorize("hasAuthority('ASSEMBLIES_MANAGE')")
    public AssemblyTopic closeVote(@PathVariable UUID topicId) {
        return service.closeVote(topicId);
    }

    @Operation(summary = "Consulta los resultados consolidados de una votación")
    @GetMapping("/topics/{topicId}/results")
    @PreAuthorize("hasAuthority('ASSEMBLIES_VIEW')")
    public TopicResults getResults(@PathVariable UUID topicId) {
        return service.getTopicResults(topicId);
    }
}

