package com.bol.lazybot.server.capability.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Installable {
    private boolean allowGlobal;
    private boolean allowRoom;
    private String callbackUrl;
}
