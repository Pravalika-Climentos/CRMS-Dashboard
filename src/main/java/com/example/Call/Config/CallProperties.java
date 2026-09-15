package com.example.Call.Config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(
    prefix = "calls"
)
@Validated
public class CallProperties {

    @NotNull
    private Duration ringTimeout =
            Duration.ofSeconds(45);

    @Valid
    @NotNull
    private WebRtc webRtc =
            new WebRtc();

    @Valid
    @NotNull
    private LiveKit liveKit =
            new LiveKit();

    @Valid
    @NotNull
    private Group group =
            new Group();

    public Duration getRingTimeout() {
        return ringTimeout;
    }

    public void setRingTimeout(
            Duration ringTimeout
    ) {
        this.ringTimeout =
                ringTimeout;
    }

    public WebRtc getWebRtc() {
        return webRtc;
    }

    public void setWebRtc(
            WebRtc webRtc
    ) {
        this.webRtc =
                webRtc;
    }

    public LiveKit getLiveKit() {
        return liveKit;
    }

    public void setLiveKit(
            LiveKit liveKit
    ) {
        this.liveKit =
                liveKit;
    }

    public Group getGroup() {
        return group;
    }

    public void setGroup(
            Group group
    ) {
        this.group =
                group;
    }

    public static class WebRtc {

        private List<String> stunUrls =
                new ArrayList<>(
                    List.of(
                        "stun:stun.l.google.com:19302",
                        "stun:stun.cloudflare.com:3478"
                    )
                );

        private List<String> turnUrls =
                new ArrayList<>();

        private String turnUsername;
        private String turnCredential;

        public List<String> getStunUrls() {
            return stunUrls;
        }

        public void setStunUrls(
                List<String> stunUrls
        ) {
            this.stunUrls =
                    stunUrls == null
                            ? new ArrayList<>()
                            : new ArrayList<>(
                                stunUrls
                            );
        }

        public List<String> getTurnUrls() {
            return turnUrls;
        }

        public void setTurnUrls(
                List<String> turnUrls
        ) {
            this.turnUrls =
                    turnUrls == null
                            ? new ArrayList<>()
                            : new ArrayList<>(
                                turnUrls
                            );
        }

        public String getTurnUsername() {
            return turnUsername;
        }

        public void setTurnUsername(
                String turnUsername
        ) {
            this.turnUsername =
                    turnUsername;
        }

        public String getTurnCredential() {
            return turnCredential;
        }

        public void setTurnCredential(
                String turnCredential
        ) {
            this.turnCredential =
                    turnCredential;
        }

        public boolean hasCompleteTurnConfiguration() {
            return turnUrls != null &&
                   !turnUrls.isEmpty() &&
                   turnUsername != null &&
                   !turnUsername.isBlank() &&
                   turnCredential != null &&
                   !turnCredential.isBlank();
        }
    }

    public static class LiveKit {

        private boolean enabled = false;

        private String url;
        private String apiKey;
        private String apiSecret;

        @NotNull
        private Duration tokenLifetime =
                Duration.ofHours(4);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(
                boolean enabled
        ) {
            this.enabled =
                    enabled;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(
                String url
        ) {
            this.url =
                    url;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(
                String apiKey
        ) {
            this.apiKey =
                    apiKey;
        }

        public String getApiSecret() {
            return apiSecret;
        }

        public void setApiSecret(
                String apiSecret
        ) {
            this.apiSecret =
                    apiSecret;
        }

        public Duration getTokenLifetime() {
            return tokenLifetime;
        }

        public void setTokenLifetime(
                Duration tokenLifetime
        ) {
            this.tokenLifetime =
                    tokenLifetime;
        }

        public boolean hasCompleteConfiguration() {
            return enabled &&
                   url != null &&
                   !url.isBlank() &&
                   apiKey != null &&
                   !apiKey.isBlank() &&
                   apiSecret != null &&
                   !apiSecret.isBlank();
        }
    }

    public static class Group {

        @Min(2)
        @Max(100)
        private int maximumParticipants = 25;

        public int getMaximumParticipants() {
            return maximumParticipants;
        }

        public void setMaximumParticipants(
                int maximumParticipants
        ) {
            this.maximumParticipants =
                    maximumParticipants;
        }
    }
}