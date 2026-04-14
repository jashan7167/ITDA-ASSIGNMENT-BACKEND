package com.itda.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModerateParticipantRequest {
    private Long userId;
    private String action;  // "mute", "unmute", "kick"
}
