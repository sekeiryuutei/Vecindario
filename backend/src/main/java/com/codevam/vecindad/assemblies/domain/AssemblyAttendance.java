package com.codevam.vecindad.assemblies.domain;

import java.time.Instant;
import java.util.UUID;

public record AssemblyAttendance(
        UUID assemblyId,
        UUID unitId,
        UUID personId,
        boolean isProxy,
        String proxyName,
        Instant checkedInAt
) {}

