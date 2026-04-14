package com.itda.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomResponse {
    private String roomId;
    private String roomName;
    private Long hostId;
    private String joinLink;
    private Long createdAt;
    private Boolean isActive;
}
