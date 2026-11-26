package vn.floodconnect.organization.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.common.storage.FileStorageService;
import vn.floodconnect.common.storage.FileStorageService.PrivateFile;
import vn.floodconnect.organization.entity.OrganizationDocument;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.OrganizationDocumentRepository;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** #6: giấy tờ pháp lý / CCCD chỉ Admin và chính tổ chức sở hữu mở được; người khác nhận 404 (không dò được mã giấy tờ). */
class OrgDocumentAccessTest {
    private final OrganizationDocumentRepository docs = mock(OrganizationDocumentRepository.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final CurrentUser current = mock(CurrentUser.class);
    private final OrgDocumentService service = new OrgDocumentService(docs, storage, current);

    private final User owner = user(10, Role.RESCUE_ORG);
    private final OrganizationDocument doc = new OrganizationDocument();

    private static User user(long id, Role role) { User u = new User(); u.setId(id); u.setRole(role); return u; }

    @BeforeEach void setUp() {
        RescueOrganization o = new RescueOrganization();
        o.setUser(owner);
        doc.setId(7L); doc.setOrg(o); doc.setStorageKey("local:org-docs/a.png");
        when(docs.findById(7L)).thenReturn(Optional.of(doc));
        when(storage.openPrivate("local:org-docs/a.png")).thenReturn(new PrivateFile(new byte[] { 1, 2 }, "image/png"));
    }

    @Test void ownerAndAdminCanOpen() {
        for (User u : List.of(owner, user(1, Role.ADMIN))) {
            when(current.get()).thenReturn(u);
            OrgDocumentService.DocFile f = service.open(7L);
            assertArrayEquals(new byte[] { 1, 2 }, f.file().data());
            assertNull(f.externalUrl());
        }
    }

    @Test void otherOrgAndCitizenGetNotFoundAndFileIsNeverRead() {
        for (User u : List.of(user(11, Role.RESCUE_ORG), user(12, Role.CITIZEN))) {
            when(current.get()).thenReturn(u);
            ApiException e = assertThrows(ApiException.class, () -> service.open(7L));
            assertEquals("NOT_FOUND", e.getCode());
        }
        verify(storage, never()).openPrivate(anyString());
    }

    @Test void legacyExternalUrlIsRedirectedOnlyForAllowedUsers() {
        doc.setStorageKey("https://placehold.co/600x400?text=Ho+so+1");
        when(current.get()).thenReturn(user(1, Role.ADMIN));
        assertEquals("https://placehold.co/600x400?text=Ho+so+1", service.open(7L).externalUrl());
    }

    @Test void replacedDocumentsOnResubmitDeleteTheirFiles() {
        RescueOrganization o = doc.getOrg();
        o.setId(5L); o.setOrgType(vn.floodconnect.organization.entity.OrgType.SOCIAL);
        doc.setSlot(vn.floodconnect.organization.entity.OrgDocSlot.MAIN);
        when(docs.findByOrgIdOrderByIdAsc(5L)).thenReturn(List.of(doc));
        when(storage.storePrivate(org.mockito.ArgumentMatchers.any(), anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new FileStorageService.Stored("local:org-docs/b.pdf", "application/pdf"));
        service.resubmit(o, false, OrgDocumentService.Uploads.of(
                List.of(new org.springframework.mock.web.MockMultipartFile("documents", "b.pdf", "application/pdf", new byte[] { 1 })), null, null, null));
        verify(storage).deletePrivateAfterCommit("local:org-docs/a.png");
    }
}
