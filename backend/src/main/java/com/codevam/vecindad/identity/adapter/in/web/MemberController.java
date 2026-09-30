package com.codevam.vecindad.identity.adapter.in.web;

import com.codevam.vecindad.identity.application.MemberService;
import com.codevam.vecindad.identity.domain.AccessHistoryEntry;
import com.codevam.vecindad.identity.domain.MemberView;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenant-members")
@Tag(name = "Usuarios de la copropiedad")
public class MemberController {

    public record AddMemberRequest(@NotBlank @Email @Size(max = 254) String email,
                                   @NotBlank @Size(max = 200) String fullName,
                                   @NotBlank @Size(max = 40) String role) {}
    public record ChangeRoleRequest(@NotBlank @Size(max = 40) String role) {}

    private final MemberService service;

    public MemberController(MemberService service) {
        this.service = service;
    }

    @Operation(summary = "Lista los miembros de la copropiedad activa")
    @GetMapping
    @PreAuthorize("hasAuthority('USERS_VIEW')")
    public PageResult<MemberView> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(CurrentUser.requireTenant().tenantId(), page, size);
    }

    @Operation(summary = "Invita/agrega un usuario a la copropiedad activa con un rol")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    public MemberView add(@Valid @RequestBody AddMemberRequest req) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        return service.addMember(me.tenantId(), me, req.email(), req.fullName(), req.role());
    }

    @Operation(summary = "Cambia el rol de un miembro")
    @PatchMapping("/{userId}/role")
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    public MemberView changeRole(@PathVariable UUID userId, @Valid @RequestBody ChangeRoleRequest req) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        return service.changeRole(me.tenantId(), me, userId, req.role());
    }

    @Operation(summary = "Suspende el acceso de un miembro")
    @PostMapping("/{userId}/suspend")
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    public MemberView suspend(@PathVariable UUID userId) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        return service.setSuspended(me.tenantId(), me, userId, true);
    }

    @Operation(summary = "Reactiva el acceso de un miembro suspendido")
    @PostMapping("/{userId}/activate")
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    public MemberView activate(@PathVariable UUID userId) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        return service.setSuspended(me.tenantId(), me, userId, false);
    }

    @Operation(summary = "Revoca el acceso (se conserva el historial)")
    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    public void revoke(@PathVariable UUID userId) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        service.revoke(me.tenantId(), me, userId);
    }

    @Operation(summary = "Historial de acceso de un miembro a esta copropiedad")
    @GetMapping("/{userId}/history")
    @PreAuthorize("hasAuthority('USERS_VIEW')")
    public List<AccessHistoryEntry> history(@PathVariable UUID userId) {
        return service.history(CurrentUser.requireTenant().tenantId(), userId);
    }
}
