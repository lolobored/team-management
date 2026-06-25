package org.lolobored.tm.customer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "logo-search")
public class LogoSearchProperties {
    private String googleApiKey = "";
    private String googleCseId = "";

    public String getGoogleApiKey() { return googleApiKey; }
    public void setGoogleApiKey(String googleApiKey) { this.googleApiKey = googleApiKey; }
    public String getGoogleCseId() { return googleCseId; }
    public void setGoogleCseId(String googleCseId) { this.googleCseId = googleCseId; }
    public boolean isConfigured() { return !googleApiKey.isBlank() && !googleCseId.isBlank(); }
}
