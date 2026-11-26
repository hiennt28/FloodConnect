package vn.floodconnect.organization.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.common.storage.FileStorageService;
import vn.floodconnect.organization.entity.OrgDocSlot;
import vn.floodconnect.organization.entity.OrgType;
import vn.floodconnect.organization.entity.OrganizationDocument;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.OrganizationDocumentRepository;
import vn.floodconnect.security.CurrentUser;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Hồ sơ xác minh tổ chức theo từng ô. */
class OrgDocumentServiceTest {
    private final OrganizationDocumentRepository docs = mock(OrganizationDocumentRepository.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final CurrentUser current = mock(CurrentUser.class);
    private final OrgDocumentService service = new OrgDocumentService(docs, storage, current);
    private final RescueOrganization org = new RescueOrganization();

    @BeforeEach void setUp() {
        org.setId(5L); org.setOrgType(OrgType.SOCIAL);
        when(storage.storePrivate(any(), anyString(), any())).thenReturn(new FileStorageService.Stored("/files/x.pdf", "application/pdf"));
    }

    private static List<MultipartFile> files(int n) {
        List<MultipartFile> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new MockMultipartFile("f", "f" + i + ".pdf", "application/pdf", new byte[]{1}));
        return out;
    }

    private static OrganizationDocument doc(OrgDocSlot slot) {
        OrganizationDocument d = new OrganizationDocument();
        d.setSlot(slot); d.setDocLabel("cũ");
        return d;
    }

    private Map<String, String> check(OrgType t, OrgDocumentService.Uploads u) {
        Map<String, String> e = new LinkedHashMap<>();
        service.check(t, u, Set.of(), e);
        return e;
    }

    @Test void volunteerNeedsBothIdSidesOneFileEach() {
        assertEquals(Set.of("idFront", "idBack"), check(OrgType.VOLUNTEER, OrgDocumentService.Uploads.of(null, null, null, files(2))).keySet());
        Map<String, String> e = check(OrgType.VOLUNTEER, OrgDocumentService.Uploads.of(null, files(2), files(1), null));
        assertEquals(Set.of("idFront"), e.keySet());
        assertTrue(e.get("idFront").contains("Chỉ tải 1 tệp"));
        assertTrue(check(OrgType.VOLUNTEER, OrgDocumentService.Uploads.of(null, files(1), files(1), null)).isEmpty());
    }

    @Test void otherTypesNeedMainDocumentAndCannotSendIdSlots() {
        assertEquals(Set.of("documents"), check(OrgType.STATE, OrgDocumentService.Uploads.of(null, null, null, files(1))).keySet());
        assertTrue(check(OrgType.LOGISTICS, OrgDocumentService.Uploads.of(files(3), null, null, null)).isEmpty()); // nhiều trang được
        assertEquals(Set.of("idFront"), check(OrgType.SOCIAL, OrgDocumentService.Uploads.of(files(1), files(1), null, null)).keySet());
        assertEquals(Set.of("documents"), check(OrgType.VOLUNTEER, OrgDocumentService.Uploads.of(files(1), files(1), files(1), null)).keySet());
    }

    @Test void savedFilesAreLabelledBySlot() {
        org.setOrgType(OrgType.VOLUNTEER);
        service.saveAll(org, OrgDocumentService.Uploads.of(null, files(1), files(1), files(1)));
        verify(docs, times(3)).save(argThat(d -> d.getSlot() != null && d.getDocLabel().equals(OrgDocumentService.label(OrgType.VOLUNTEER, d.getSlot()))));
    }

    @Test void resubmitReplacesOnlyTheSlotsThatGotNewFiles() {
        OrganizationDocument main = doc(OrgDocSlot.MAIN), evidence = doc(OrgDocSlot.EVIDENCE);
        when(docs.findByOrgIdOrderByIdAsc(5L)).thenReturn(List.of(main, evidence));

        service.resubmit(org, false, OrgDocumentService.Uploads.of(null, null, null, files(1)));

        verify(docs).deleteAll(List.of(evidence)); // giấy tờ chính giữ nguyên
        verify(docs).save(argThat(d -> d.getSlot() == OrgDocSlot.EVIDENCE));
    }

    @Test void oldUnslottedFileCountsAsMainDocument() {
        when(docs.findByOrgIdOrderByIdAsc(5L)).thenReturn(List.of(doc(null)));
        assertDoesNotThrow(() -> service.resubmit(org, false, OrgDocumentService.Uploads.of(null, null, null, null)));
        verify(docs).deleteAll(List.of());
    }

    @Test void changingTypeRequiresLegalDocumentsOfTheNewTypeAndKeepsEverythingOnError() {
        org.setOrgType(OrgType.VOLUNTEER); // vừa đổi từ SOCIAL
        when(docs.findByOrgIdOrderByIdAsc(5L)).thenReturn(List.of(doc(OrgDocSlot.MAIN), doc(OrgDocSlot.EVIDENCE)));

        ApiException e = assertThrows(ApiException.class,
                () -> service.resubmit(org, true, OrgDocumentService.Uploads.of(null, files(1), null, null)));

        assertEquals(Set.of("idBack"), e.getErrors().keySet());
        verify(docs, never()).deleteAll(any());
        verify(docs, never()).save(any());
        verifyNoInteractions(storage);
    }

    @Test void changingTypeDropsOldLegalDocumentsButKeepsEvidence() {
        org.setOrgType(OrgType.VOLUNTEER);
        OrganizationDocument main = doc(OrgDocSlot.MAIN), evidence = doc(OrgDocSlot.EVIDENCE);
        when(docs.findByOrgIdOrderByIdAsc(5L)).thenReturn(List.of(main, evidence));

        service.resubmit(org, true, OrgDocumentService.Uploads.of(null, files(1), files(1), null));

        verify(docs).deleteAll(List.of(main));
        verify(docs, times(2)).save(any());
    }
}
