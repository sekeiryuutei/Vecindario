package com.codevam.vecindad.assemblies.application.port.out;

import com.codevam.vecindad.assemblies.domain.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssemblyPort {
    Assembly insertAssembly(Assembly a);
    Optional<Assembly> findAssemblyById(UUID id);
    List<Assembly> listAssemblies();
    boolean updateAssemblyStatus(UUID id, AssemblyStatus status);

    boolean registerAttendance(AssemblyAttendance att);
    List<AssemblyAttendance> listAttendances(UUID assemblyId);
    boolean isUnitCheckedIn(UUID assemblyId, UUID unitId);
    QuorumSummary calculateQuorum(UUID assemblyId);

    AssemblyTopic insertTopic(AssemblyTopic t);
    Optional<AssemblyTopic> findTopicById(UUID topicId);
    List<AssemblyTopic> listTopics(UUID assemblyId);
    boolean updateTopicStatus(UUID topicId, TopicStatus status);

    boolean insertVote(AssemblyVote vote);
    List<AssemblyVote> listVotes(UUID topicId);
    boolean hasUnitVoted(UUID topicId, UUID unitId);
    BigDecimal getUnitCoefficient(UUID unitId);
}

