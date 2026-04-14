package com.itda.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomLifecycleResponse {
    private String roomId;
    private String message;
    private Integer closedConnections;
}
