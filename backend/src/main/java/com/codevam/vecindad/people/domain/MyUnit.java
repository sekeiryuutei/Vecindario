package com.codevam.vecindad.people.domain;

import java.util.List;
import java.util.UUID;

public record MyUnit(UUID unitId, String identifier, String tower, String unitType, List<String> relations) {}
