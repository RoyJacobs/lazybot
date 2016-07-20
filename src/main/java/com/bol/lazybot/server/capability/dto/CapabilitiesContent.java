package com.bol.lazybot.server.capability.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CapabilitiesContent {
    private Installable installable;

    @JsonProperty("hipchatApiConsumer")
    private HipChatApiConsumer hipChatApiConsumer;
}
