package com.codevam.vecindad.assemblies.application;

import com.codevam.vecindad.assemblies.application.port.out.AssemblyPort;
import com.codevam.vecindad.assemblies.domain.*;
import com.codevam.vecindad.shared.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
public class AssemblyService {

    public record CreateAssemblyCommand(
            String title,
            AssemblyType type,
            Instant scheduledAt,
            String location,
            boolean isVirtual,
            String meetingLink,
            BigDecimal requiredQuorumPercentage
    ) {}

    public record CreateTopicCommand(
            int orderNum,
            String title,
            String description,
            VotingType votingType,
            boolean requiresQualifiedMajority,
            List<String> options
    ) {}

    private final AssemblyPort port;

    public AssemblyService(AssemblyPort port) {
        this.port = port;
    }

    public List<Assembly> listAssemblies() {
        return port.listAssemblies();
    }

    public Assembly getAssembly(UUID id) {
        return port.findAssemblyById(id)
                .orElseThrow(() -> ApiException.notFound("Asamblea no encontrada."));
    }

    @Transactional
    public Assembly createAssembly(CreateAssemblyCommand cmd) {
        Assembly a = new Assembly(
                UUID.randomUUID(),
                cmd.title(),
                cmd.type() == null ? AssemblyType.ORDINARIA : cmd.type(),
                cmd.scheduledAt(),
                AssemblyStatus.SCHEDULED,
                cmd.location(),
                cmd.isVirtual(),
                cmd.meetingLink(),
                cmd.requiredQuorumPercentage() == null ? new BigDecimal("51.00") : cmd.requiredQuorumPercentage(),
                Instant.now(),
                Instant.now()
        );
        return port.insertAssembly(a);
    }

    @Transactional
    public Assembly updateStatus(UUID id, AssemblyStatus status) {
        getAssembly(id);
        port.updateAssemblyStatus(id, status);
        return getAssembly(id);
    }

    @Transactional
    public QuorumSummary registerAttendance(UUID assemblyId, UUID unitId, UUID personId, boolean isProxy, String proxyName) {
        getAssembly(assemblyId);
        port.registerAttendance(new AssemblyAttendance(
                assemblyId, unitId, personId, isProxy, proxyName, Instant.now()
        ));
        return port.calculateQuorum(assemblyId);
    }

    public QuorumSummary getQuorum(UUID assemblyId) {
        getAssembly(assemblyId);
        return port.calculateQuorum(assemblyId);
    }

    public List<AssemblyAttendance> listAttendances(UUID assemblyId) {
        getAssembly(assemblyId);
        return port.listAttendances(assemblyId);
    }

    public List<AssemblyTopic> listTopics(UUID assemblyId) {
        getAssembly(assemblyId);
        return port.listTopics(assemblyId);
    }

    public AssemblyTopic getTopic(UUID topicId) {
        return port.findTopicById(topicId)
                .orElseThrow(() -> ApiException.notFound("Punto de votación no encontrado."));
    }

    @Transactional
    public AssemblyTopic createTopic(UUID assemblyId, CreateTopicCommand cmd) {
        getAssembly(assemblyId);
        List<String> opts = (cmd.options() == null || cmd.options().isEmpty())
                ? List.of("A FAVOR", "EN CONTRA", "ABSTENCION")
                : cmd.options();

        AssemblyTopic t = new AssemblyTopic(
                UUID.randomUUID(),
                assemblyId,
                cmd.orderNum(),
                cmd.title(),
                cmd.description(),
                cmd.votingType() == null ? VotingType.COEFFICIENT : cmd.votingType(),
                TopicStatus.PENDING,
                cmd.requiresQualifiedMajority(),
                opts,
                Instant.now()
        );
        return port.insertTopic(t);
    }

    @Transactional
    public AssemblyTopic openVote(UUID topicId) {
        getTopic(topicId);
        port.updateTopicStatus(topicId, TopicStatus.VOTING_OPEN);
        return getTopic(topicId);
    }

    @Transactional
    public AssemblyTopic closeVote(UUID topicId) {
        getTopic(topicId);
        port.updateTopicStatus(topicId, TopicStatus.VOTING_CLOSED);
        return getTopic(topicId);
    }

    @Transactional
    public AssemblyVote castVote(UUID topicId, UUID unitId, UUID personId, String choice) {
        AssemblyTopic topic = getTopic(topicId);
        if (topic.status() != TopicStatus.VOTING_OPEN) {
            throw ApiException.conflict("VOTING_NOT_OPEN", "La votación no se encuentra abierta.");
        }

        if (!topic.options().contains(choice)) {
            throw ApiException.badRequest("INVALID_OPTION", "La opción seleccionada no es válida para este tema.");
        }

        if (!port.isUnitCheckedIn(topic.assemblyId(), unitId)) {
            throw ApiException.conflict("UNIT_NOT_CHECKED_IN", "El inmueble no ha registrado su asistencia a la asamblea.");
        }

        if (port.hasUnitVoted(topicId, unitId)) {
            throw ApiException.conflict("VOTE_ALREADY_CAST", "El inmueble ya emitió su voto para este tema.");
        }

        BigDecimal weight = BigDecimal.ONE;
        if (topic.votingType() == VotingType.COEFFICIENT) {
            weight = port.getUnitCoefficient(unitId);
        }

        AssemblyVote vote = new AssemblyVote(
                UUID.randomUUID(),
                topicId,
                unitId,
                personId,
                choice,
                weight,
                Instant.now()
        );
        port.insertVote(vote);
        return vote;
    }

    public TopicResults getTopicResults(UUID topicId) {
        AssemblyTopic topic = getTopic(topicId);
        List<AssemblyVote> votes = port.listVotes(topicId);

        Map<String, BigDecimal> tallies = new HashMap<>();
        for (String opt : topic.options()) {
            tallies.put(opt, BigDecimal.ZERO);
        }

        BigDecimal totalWeight = BigDecimal.ZERO;
        for (AssemblyVote v : votes) {
            BigDecimal current = tallies.getOrDefault(v.choice(), BigDecimal.ZERO);
            tallies.put(v.choice(), current.add(v.weight()));
            totalWeight = totalWeight.add(v.weight());
        }

        Map<String, BigDecimal> percentages = new HashMap<>();
        String winner = null;
        BigDecimal maxWeight = BigDecimal.valueOf(-1);

        for (Map.Entry<String, BigDecimal> e : tallies.entrySet()) {
            BigDecimal pct = BigDecimal.ZERO;
            if (totalWeight.compareTo(BigDecimal.ZERO) > 0) {
                pct = e.getValue().divide(totalWeight, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
            }
            percentages.put(e.getKey(), pct);

            if (e.getValue().compareTo(maxWeight) > 0) {
                maxWeight = e.getValue();
                winner = e.getKey();
            }
        }

        return new TopicResults(
                topic.id(),
                topic.title(),
                topic.votingType(),
                topic.status(),
                votes.size(),
                totalWeight,
                tallies,
                percentages,
                votes.isEmpty() ? null : winner
        );
    }
}
