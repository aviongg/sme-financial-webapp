package com.app.sme_health_backend.identity.controller;

import com.app.sme_health_backend.identity.dto.*;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class MembershipController {
    private final MembershipService service;
    private final BusinessAuthorizationService authorization;
    private final ActiveBusinessContext activeBusiness;
    public MembershipController(MembershipService service, BusinessAuthorizationService authorization, ActiveBusinessContext activeBusiness) {
        this.service = service; this.authorization = authorization; this.activeBusiness = activeBusiness;
    }

    @GetMapping("/memberships")
    public List<MembershipResponse> list(HttpServletRequest request) {
        return service.list(authorization.requirePermission(request, BusinessPermission.MEMBERSHIP_MANAGE));
    }
    @PostMapping("/memberships") @ResponseStatus(HttpStatus.CREATED)
    public MembershipResponse invite(@Valid @RequestBody InviteMemberRequest body, HttpServletRequest request) {
        return service.invite(authorization.requirePermission(request, BusinessPermission.MEMBERSHIP_MANAGE), body);
    }
    @PatchMapping("/memberships/{id}")
    public MembershipResponse update(@PathVariable UUID id, @RequestBody UpdateMembershipRequest body, HttpServletRequest request) {
        return service.update(authorization.requirePermission(request, BusinessPermission.MEMBERSHIP_MANAGE), id, body);
    }
    @DeleteMapping("/memberships/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id, HttpServletRequest request) {
        service.remove(authorization.requirePermission(request, BusinessPermission.MEMBERSHIP_MANAGE), id);
    }
    @GetMapping("/invitations")
    public List<InvitationResponse> invitations() { return service.invitations(activeBusiness.requireAuthenticatedUserId()); }
    @PostMapping("/invitations/{id}/accept") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@PathVariable UUID id) { service.answerInvitation(activeBusiness.requireAuthenticatedUserId(), id, true); }
    @PostMapping("/invitations/{id}/decline") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@PathVariable UUID id) { service.answerInvitation(activeBusiness.requireAuthenticatedUserId(), id, false); }
}
