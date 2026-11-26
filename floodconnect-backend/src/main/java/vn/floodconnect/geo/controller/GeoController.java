package vn.floodconnect.geo.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.floodconnect.geo.dto.GeoDtos.LocateResult;
import vn.floodconnect.geo.dto.GeoDtos.WardDetail;
import vn.floodconnect.geo.service.GeoService;

/** Địa giới hành chính cho form SOS (mọi tài khoản đã đăng nhập, theo quy tắc chung anyRequest().authenticated()). */
@RestController
@RequestMapping("/api/geo")
@RequiredArgsConstructor
public class GeoController {
    private final GeoService geo;

    /** Tọa độ thuộc phường/xã nào: ward = null nếu không thuộc xã nào; approximate = true nếu chỉ là xã gần nhất (khe hở ranh giới). */
    @GetMapping("/locate")
    public LocateResult locate(@RequestParam Double lat, @RequestParam Double lng) { return geo.locate(lat, lng); }

    /** Điểm đại diện (luôn nằm trong xã) và đường viền GeoJSON của một phường/xã. */
    @GetMapping("/wards/{code}")
    public WardDetail ward(@PathVariable String code) { return geo.ward(code); }
}
