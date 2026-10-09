package com.codevam.vecindad.unit;

import com.codevam.vecindad.assemblies.application.AssemblyService;
import com.codevam.vecindad.assemblies.application.port.out.AssemblyPort;
import com.codevam.vecindad.assemblies.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class AssemblyServiceTest {

    private AssemblyPort port;
    private AssemblyService service;

    private UUID asmId;
    private UUID topicId;

    @BeforeEach
    void setUp() {
        port = Mockito.mock(AssemblyPort.class);
        service = new AssemblyService(port);

        asmId = UUID.randomUUID();
        topicId = UUID.randomUUID();

        Assembly asm = new Assembly(
                asmId, "Asamblea 2026", AssemblyType.ORDINARIA, Instant.now(),
                AssemblyStatus.IN_PROGRESS, "Auditorio", false, null,
                new BigDecimal("51.00"), Instant.now(), Instant.now()
        );
        when(port.findAssemblyById(asmId)).thenReturn(Optional.of(asm));

        AssemblyTopic topic = new AssemblyTopic(
                topicId, asmId, 1, "Aprobación de Presupuesto", null,
                VotingType.COEFFICIENT, TopicStatus.VOTING_OPEN, false,
                List.of("A FAVOR", "EN CONTRA", "ABSTENCION"), Instant.now()
        );
        when(port.findTopicById(topicId)).thenReturn(Optional.of(topic));
    }

    @Test
    void calculatesQuorumCorrectly() {
        QuorumSummary expected = new QuorumSummary(
                55, 100, new BigDecimal("55.40"), new BigDecimal("51.00"), true
        );
        when(port.calculateQuorum(asmId)).thenReturn(expected);

        QuorumSummary q = service.getQuorum(asmId);
        assertTrue(q.hasQuorum());
        assertEquals(new BigDecimal("55.40"), q.currentQuorumPercentage());
    }

    @Test
    void talliesWeightedVotesAndDeclaresWinner() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();

        // 3 votos ponderados por coeficiente
        List<AssemblyVote> votes = List.of(
                new AssemblyVote(UUID.randomUUID(), topicId, u1, null, "A FAVOR", new BigDecimal("0.350000"), Instant.now()),
                new AssemblyVote(UUID.randomUUID(), topicId, u2, null, "A FAVOR", new BigDecimal("0.250000"), Instant.now()),
                new AssemblyVote(UUID.randomUUID(), topicId, u3, null, "EN CONTRA", new BigDecimal("0.150000"), Instant.now())
        );
        when(port.listVotes(topicId)).thenReturn(votes);

        TopicResults results = service.getTopicResults(topicId);
        assertEquals(3, results.totalVotes());
        assertEquals("A FAVOR", results.winningOption());
        assertEquals(new BigDecimal("0.600000"), results.voteTallies().get("A FAVOR"));
        assertEquals(new BigDecimal("0.150000"), results.voteTallies().get("EN CONTRA"));
        assertEquals(new BigDecimal("80.00"), results.percentageTallies().get("A FAVOR"));
        assertEquals(new BigDecimal("20.00"), results.percentageTallies().get("EN CONTRA"));
    }
}

