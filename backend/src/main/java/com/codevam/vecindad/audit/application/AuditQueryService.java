package com.codevam.vecindad.audit.application;

import com.codevam.vecindad.audit.application.port.out.AuditPort;
import com.codevam.vecindad.audit.domain.AuditLogView;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.model.Paging;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditQueryService {
    private final AuditPort port;

    public AuditQueryService(AuditPort port) {
        this.port = port;
    }

    public PageResult<AuditLogView> search(UUID tenantId, String action, Instant from, Instant to, int page, int size) {
        return port.search(tenantId, blankToNull(action), from, to, Paging.page(page), Paging.size(size));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
