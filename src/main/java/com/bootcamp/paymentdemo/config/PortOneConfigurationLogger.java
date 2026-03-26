package com.bootcamp.paymentdemo.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneConfigurationLogger {

    private final PortOneProperties portOneProperties;

    @PostConstruct
    public void logConfiguration() {
        log.info(
                "PortOne 설정 확인 - baseUrl={}, storeId={}, kgChannel={}, tossChannel={}, secretMask={}",
                nullSafe(portOneProperties.getApi() != null ? portOneProperties.getApi().getBaseUrl() : null),
                mask(portOneProperties.getStore() != null ? portOneProperties.getStore().getId() : null),
                mask(portOneProperties.getChannel() != null ? portOneProperties.getChannel().get("kg-inicis") : null),
                mask(portOneProperties.getChannel() != null ? portOneProperties.getChannel().get("toss") : null),
                mask(portOneProperties.getApi() != null ? portOneProperties.getApi().getSecret() : null)
        );
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        if (value.length() <= 8) {
            return value.charAt(0) + "***" + value.charAt(value.length() - 1);
        }
        return value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "<empty>" : value;
    }
}
