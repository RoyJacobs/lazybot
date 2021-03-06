package org.royjacobs.lazybot.hipchat.server.install.dto;

import lombok.Data;

@Data
public class InstalledInformation {
    private String capabilitiesUrl;
    private String oauthId;
    private String oauthSecret;
    private String roomId;
    private String groupId;
}
