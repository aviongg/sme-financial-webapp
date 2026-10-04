package com.app.sme_health_backend.identity.service;

import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.identity.dto.*;
import com.app.sme_health_backend.identity.entity.*;
import com.app.sme_health_backend.identity.model.*;
import com.app.sme_health_backend.identity.repository.*;
import com.app.sme_health_backend.shared.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MembershipServiceTests {
    private final BusinessMembershipRepository memberships = mock(BusinessMembershipRepository.class);
    private final BusinessRepository businesses = mock(BusinessRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final MembershipService service = new MembershipService(memberships, businesses, users, mock(SecurityAuditService.class));
    private final UUID owner = UUID.randomUUID(), businessId = UUID.randomUUID(), inviteeId = UUID.randomUUID();
    private final BusinessAccessContext context = new BusinessAccessContext(owner, businessId, MembershipRole.OWNER);
    private BusinessMembership ownerMembership;
    @BeforeEach void setup() {
        Business business = new Business(businessId); business.setBusinessName("Team business");
        when(businesses.findByIdForUpdate(businessId)).thenReturn(Optional.of(business));
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        ownerMembership = new BusinessMembership(owner, businessId, MembershipRole.OWNER, MembershipStatus.ACTIVE);
        ownerMembership.setId(UUID.randomUUID());
        when(memberships.findByUserIdAndBusinessId(owner, businessId)).thenReturn(Optional.of(ownerMembership));
        when(memberships.save(any())).thenAnswer(i -> { BusinessMembership m = i.getArgument(0); if(m.getId() == null) m.setId(UUID.randomUUID()); return m; });
    }
    @Test void resolvesInviteeByNormalizedEmailAndDoesNotGrantAccessBeforeAcceptance() {
        AppUser user = new AppUser(); user.setId(inviteeId); user.setEmail("invited@example.test");
        when(users.findByEmail("invited@example.test")).thenReturn(Optional.of(user));
        when(users.findById(inviteeId)).thenReturn(Optional.of(user));
        MembershipResponse result = service.invite(context, new InviteMemberRequest(" INVITED@EXAMPLE.TEST ", MembershipRole.ACCOUNTANT));
        assertEquals(MembershipStatus.INVITED, result.status());
        assertEquals(MembershipRole.ACCOUNTANT, result.role());
        verify(memberships).existsByUserIdAndBusinessId(inviteeId, businessId);
    }
    @Test void preventsOwnerInvitesAndUnregisteredUsers() {
        assertThrows(IllegalArgumentException.class, () -> service.invite(context, new InviteMemberRequest("missing@example.test", MembershipRole.OWNER)));
        assertThrows(IllegalArgumentException.class, () -> service.invite(context, new InviteMemberRequest("missing@example.test", MembershipRole.VIEWER)));
        verify(memberships, never()).save(any());
    }
    @Test void protectsCurrentOwnerAgainstAllChangesAndRemoval() {
        when(memberships.findScopedForUpdate(ownerMembership.getId(), businessId)).thenReturn(Optional.of(ownerMembership));
        assertThrows(IllegalArgumentException.class, () -> service.update(context, ownerMembership.getId(), new UpdateMembershipRequest(MembershipRole.VIEWER, null)));
        assertThrows(IllegalArgumentException.class, () -> service.update(context, ownerMembership.getId(), new UpdateMembershipRequest(null, MembershipStatus.SUSPENDED)));
        assertThrows(IllegalArgumentException.class, () -> service.remove(context, ownerMembership.getId()));
        verify(memberships, never()).delete(any());
    }
    @Test void invitedUserAloneAcceptsOrDeclinesAndOwnerCannotActivateInvitation() {
        UUID id = UUID.randomUUID();
        BusinessMembership invitation = new BusinessMembership(inviteeId, businessId, MembershipRole.MANAGER, MembershipStatus.INVITED); invitation.setId(id);
        when(memberships.findInvitationForUpdate(id, inviteeId)).thenReturn(Optional.of(invitation));
        when(memberships.findScopedForUpdate(id, businessId)).thenReturn(Optional.of(invitation));
        assertThrows(ResourceNotFoundException.class, () -> service.answerInvitation(owner, id, true));
        assertThrows(IllegalArgumentException.class, () -> service.update(context, id, new UpdateMembershipRequest(null, MembershipStatus.ACTIVE)));
        service.answerInvitation(inviteeId, id, true);
        assertEquals(MembershipStatus.ACTIVE, invitation.getStatus());
        invitation.setStatus(MembershipStatus.INVITED);
        service.answerInvitation(inviteeId, id, false);
        verify(memberships).delete(invitation);
    }
    @Test void nonOwnerAndSuspendedActorCannotManageMemberships() {
        for (MembershipRole role : List.of(MembershipRole.ACCOUNTANT, MembershipRole.MANAGER, MembershipRole.VIEWER)) {
            ownerMembership.setRole(role);
            assertThrows(AccessDeniedException.class, () -> service.list(context));
            assertThrows(AccessDeniedException.class, () -> service.invite(context, new InviteMemberRequest("user@example.test", role)));
        }
        ownerMembership.setRole(MembershipRole.OWNER); ownerMembership.setStatus(MembershipStatus.SUSPENDED);
        assertThrows(AccessDeniedException.class, () -> service.remove(context, UUID.randomUUID()));
    }
    @Test void wrongBusinessMembershipIdentifiersReturnGenericNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.update(context, UUID.randomUUID(), new UpdateMembershipRequest(MembershipRole.VIEWER, null)));
        assertThrows(ResourceNotFoundException.class, () -> service.remove(context, UUID.randomUUID()));
    }
}
