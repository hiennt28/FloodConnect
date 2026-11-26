package vn.floodconnect.incident.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.floodconnect.common.util.Geo;
import vn.floodconnect.incident.dto.IncidentDtos.MapData;
import vn.floodconnect.incident.dto.IncidentDtos.MapMarker;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.organization.service.OrgContext;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.User;
import java.util.*;

/** Bản đồ cứu hộ: marker khác nhau theo vai trò. */
@Service
@RequiredArgsConstructor
public class MapService {
    private final IncidentRepository incidents;
    private final RescueOrganizationRepository orgs;
    private final CurrentUser current;
    private final OrgContext orgContext;

    private MapMarker sosMarker(Incident i, String kind, String extra) {
        return new MapMarker(kind, i.getId(), i.getCode(), i.getLatitude(), i.getLongitude(), i.getStatus().name(), i.getDangerLevel().name(), extra);
    }

    /** Ca của người khác trên bản đồ công khai: tọa độ làm tròn (~100 m), giống IncidentMapper khi không có quyền xem chi tiết. */
    private MapMarker publicSosMarker(Incident i) {
        return new MapMarker("SOS", i.getId(), i.getCode(), Geo.coarse(i.getLatitude()), Geo.coarse(i.getLongitude()),
                i.getStatus().name(), i.getDangerLevel().name(), null);
    }

    /** Admin: chỉ trả trụ sở các tổ chức; ca SOS của bản đồ điều phối lấy từ /api/admin/sos/map (AdminSosMapService, có lọc và giới hạn). */
    @Transactional(readOnly = true)
    public MapData markers() {
        User u = current.get();
        List<MapMarker> out = new ArrayList<>();
        Double myLat = u.getLatitude(), myLng = u.getLongitude();

        switch (u.getRole()) {
            case ADMIN -> {
                for (RescueOrganization o : orgs.findByStatus(OrgStatus.ACTIVE))
                    if (o.getLatitude() != null)
                        out.add(new MapMarker("ORG", o.getId(), o.getName(), o.getLatitude(), o.getLongitude(), o.getOperatingStatus().name(), null, null));
            }
            case RESCUE_ORG -> {
                RescueOrganization o = orgContext.of(u);
                if (myLat == null) { myLat = o.getLatitude(); myLng = o.getLongitude(); }
                for (Incident i : incidents.findByAssignedOrgIdOrderByCreatedAtDesc(o.getId())) {
                    if (i.getStatus() == IncidentStatus.ASSIGNED) out.add(sosMarker(i, "SOS_ASSIGNED", null));
                    else if (i.getStatus() == IncidentStatus.ACCEPTED) out.add(sosMarker(i, "SOS_ACCEPTED", null));
                }
            }
            default -> {
                // Người dân: ca SOS lũ lụt đang hoạt động (công khai), và tổ chức đang xử lý ca của mình
                for (Incident i : incidents.findByStatusIn(SosService.ACTIVE)) {
                    if (i.getDangerLevel() == DangerLevel.KHONG && !i.getCitizen().getId().equals(u.getId())) continue;
                    boolean mine = i.getCitizen().getId().equals(u.getId());
                    out.add(mine ? sosMarker(i, "SOS", "mine") : publicSosMarker(i));
                    if (mine && i.getAssignedOrg() != null && i.getAssignedOrg().getLatitude() != null)
                        out.add(new MapMarker("ORG", i.getAssignedOrg().getId(), i.getAssignedOrg().getName(),
                                i.getAssignedOrg().getLatitude(), i.getAssignedOrg().getLongitude(), i.getStatus().name(), null, null));
                }
            }
        }
        return new MapData(out, myLat, myLng);
    }
}
