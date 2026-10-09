package com.app.sme_health_backend.identity.service;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.identity.dto.*;
import com.app.sme_health_backend.identity.entity.*;
import com.app.sme_health_backend.identity.model.*;
import com.app.sme_health_backend.identity.repository.*;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class MembershipService {
    private final BusinessMembershipRepository memberships;
    private final BusinessRepository businesses;
    private final AppUserRepository users;
    private final SecurityAuditService audit;

    public MembershipService(BusinessMembershipRepository memberships, BusinessRepository businesses,
                             AppUserRepository users, SecurityAuditService audit) {
        this.memberships = memberships; this.businesses = businesses; this.users = users; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> list(BusinessAccessContext context) {
        requireManager(context);
        return memberships.findByBusinessId(context.businessId()).stream()
                .sorted(Comparator.comparing(BusinessMembership::getCreatedAt)).map(this::response).toList();
    }

    @Transactional
    public MembershipResponse invite(BusinessAccessContext context, InviteMemberRequest request) {
        lockBusiness(context); requireManager(context); validateRole(request.role());
        AppUser invitee = users.findByEmail(request.email().trim().toLowerCase(Locale.ROOT))
                .filter(u -> u.getAccountStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("An active registered account is required"));
        if (memberships.existsByUserIdAndBusinessId(invitee.getId(), context.businessId())) {
            throw new DuplicateResourceException("Membership or invitation already exists");
        }
        BusinessMembership membership = memberships.save(new BusinessMembership(invitee.getId(), context.businessId(),
                request.role(), MembershipStatus.INVITED));
        record(AuditEventType.MEMBERSHIP_INVITED, context.userId(), membership);
        return response(membership);
    }

    @Transactional
    public MembershipResponse update(BusinessAccessContext context, UUID id, UpdateMembershipRequest request) {
        lockBusiness(context); requireManager(context);
        BusinessMembership membership = managedMembership(context.businessId(), id);
        protectOwner(membership);
        if (request.role() == null && request.status() == null) throw new IllegalArgumentException("A role or status is required");
        if (request.role() != null) {
            validateRole(request.role()); membership.setRole(request.role());
        }
        if (request.status() != null) {
            if (membership.getStatus() == MembershipStatus.INVITED || request.status() == MembershipStatus.INVITED) {
                throw new IllegalArgumentException("Only the invited account can accept an invitation");
            }
            membership.setStatus(request.status());
        }
        memberships.save(membership);
        if (request.role() != null) record(AuditEventType.MEMBERSHIP_ROLE_CHANGED, context.userId(), membership);
        if (request.status() != null) record(request.status() == MembershipStatus.SUSPENDED
                ? AuditEventType.MEMBERSHIP_SUSPENDED : AuditEventType.MEMBERSHIP_REACTIVATED, context.userId(), membership);
        return response(membership);
    }

    @Transactional
    public void remove(BusinessAccessContext context, UUID id) {
        lockBusiness(context); requireManager(context);
        BusinessMembership membership = managedMembership(context.businessId(), id);
        protectOwner(membership);
        memberships.delete(membership);
        record(AuditEventType.MEMBERSHIP_REMOVED, context.userId(), membership);
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> invitations(UUID userId) {
        return memberships.findByUserId(userId).stream().filter(m -> m.getStatus() == MembershipStatus.INVITED)
                .flatMap(m -> businesses.findById(m.getBusinessId()).filter(b -> "ACTIVE".equals(b.getStatus()))
                        .map(b -> new InvitationResponse(m.getId(), b.getBusinessName(), m.getRole(), m.getCreatedAt())).stream())
                .toList();
    }

    @Transactional
    public void answerInvitation(UUID userId, UUID id, boolean accept) {
        BusinessMembership membership = memberships.findInvitationForUpdate(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
        businesses.findById(membership.getBusinessId()).filter(b -> "ACTIVE".equals(b.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
        // Normal invitations can never create an owner, including legacy/malformed rows.
        validateRole(membership.getRole());
        if (accept) {
            membership.setStatus(MembershipStatus.ACTIVE); memberships.save(membership);
        } else memberships.delete(membership);
        record(accept ? AuditEventType.MEMBERSHIP_ACCEPTED : AuditEventType.MEMBERSHIP_DECLINED, userId, membership);
    }

    private void requireManager(BusinessAccessContext context) {
        memberships.findByUserIdAndBusinessId(context.userId(), context.businessId())
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE && m.getRole() == MembershipRole.OWNER)
                .orElseThrow(() -> new AccessDeniedException("Membership management access denied"));
    }

    private void lockBusiness(BusinessAccessContext context) {
        businesses.findByIdForUpdate(context.businessId()).filter(b -> "ACTIVE".equals(b.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
    }

    private BusinessMembership managedMembership(UUID businessId, UUID id) {
        return memberships.findScopedForUpdate(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found"));
    }

    private void protectOwner(BusinessMembership membership) {
        if (membership.getRole() == MembershipRole.OWNER) throw new IllegalArgumentException("Owner membership cannot be changed or removed");
    }

    private void validateRole(MembershipRole role) {
        if (role == null || role == MembershipRole.OWNER) throw new IllegalArgumentException("Select ACCOUNTANT, MANAGER or VIEWER");
    }

    private MembershipResponse response(BusinessMembership m) {
        String email = users.findById(m.getUserId()).map(AppUser::getEmail).orElse("");
        return new MembershipResponse(m.getId(), email, m.getRole(), m.getStatus(), m.getCreatedAt());
    }

    private void record(AuditEventType type, UUID actor, BusinessMembership membership) {
        audit.logSuccess(type, actor, null, membership.getBusinessId(), "membership", membership.getId().toString(),
                Map.of("role", membership.getRole().name(), "status", membership.getStatus().name()));
    }
}
