package com.codevam.vecindad.board.application.port.out;

import com.codevam.vecindad.board.domain.BoardMember;
import com.codevam.vecindad.board.domain.BoardPosition;
import com.codevam.vecindad.board.domain.BoardStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoardPort {
    Optional<BoardMember> findById(UUID id);
    List<BoardMember> list(BoardStatus status);
    boolean activePersonExists(UUID personId, UUID excludeId);
    boolean activePositionExists(BoardPosition position, UUID excludeId);
    void insert(BoardMember member);
    boolean update(BoardMember member);
    boolean softDelete(UUID id, UUID by, Instant at);
}
