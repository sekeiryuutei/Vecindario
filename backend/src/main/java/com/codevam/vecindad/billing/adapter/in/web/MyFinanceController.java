package com.codevam.vecindad.billing.adapter.in.web;

import com.codevam.vecindad.billing.application.StatementService;
import com.codevam.vecindad.billing.domain.Statement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/my")
@Tag(name = "Cartera - residente")
public class MyFinanceController {
    private final StatementService statements;

    public MyFinanceController(StatementService statements) {
        this.statements = statements;
    }

    @Operation(summary = "Estado de cuenta de uno de MIS inmuebles (unitId). Un propietario con varios inmuebles elige cuál. 404 si el inmueble no es suyo")
    @GetMapping("/statement")
    @PreAuthorize("hasAuthority('FINANCE_VIEW_OWN')")
    public Statement myStatement(@RequestParam UUID unitId,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return statements.myStatement(unitId, from, to);
    }
}
