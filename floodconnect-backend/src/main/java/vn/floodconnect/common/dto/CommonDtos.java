package vn.floodconnect.common.dto;

public final class CommonDtos {
    private CommonDtos() {}

    public record MessageResponse(String message) {}
    public record MediaDto(Long id, String url, String type) {}
}
