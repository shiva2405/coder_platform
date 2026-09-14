package com.coderplatform.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void prefersNginxRealIpOverClientSuppliedForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.9, 198.51.100.10");
        request.addHeader("X-Real-IP", "198.51.100.10");
        request.setRemoteAddr("10.0.0.2");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("198.51.100.10");
    }

    @Test
    void usesRightMostForwardedHopWhenRealIpIsMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.9, 198.51.100.10");
        request.setRemoteAddr("10.0.0.2");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("198.51.100.10");
    }

    @Test
    void ignoresSpoofedLeftMostForwardedAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "1.2.3.4, 198.51.100.20");

        assertThat(ClientIpResolver.resolve(request)).isNotEqualTo("1.2.3.4");
        assertThat(ClientIpResolver.resolve(request)).isEqualTo("198.51.100.20");
    }

    @Test
    void fallsBackToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.15");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("192.0.2.15");
    }

    @Test
    void stripsIpv4PortAndMappedIpv6Prefix() {
        assertThat(ClientIpResolver.normalize("203.0.113.10:443")).isEqualTo("203.0.113.10");
        assertThat(ClientIpResolver.normalize("::ffff:198.51.100.7")).isEqualTo("198.51.100.7");
        assertThat(ClientIpResolver.normalize("[2001:db8::1]:443")).isEqualTo("2001:db8::1");
    }
}
