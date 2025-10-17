package com.sunil.finintel.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    private String runWith(String incomingHeader, MockHttpServletResponse response) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        if (incomingHeader != null) {
            request.addHeader(RequestIds.HEADER, incomingHeader);
        }
        AtomicReference<String> seenInChain = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> seenInChain.set(MDC.get(RequestIds.MDC_KEY)));
        return seenInChain.get();
    }

    @Test
    void reusesClientRequestIdAndEchoesIt() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seen = runWith("client-123", response);

        assertThat(seen).isEqualTo("client-123");
        assertThat(response.getHeader(RequestIds.HEADER)).isEqualTo("client-123");
    }

    @Test
    void generatesIdWhenMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seen = runWith(null, response);

        assertThat(seen).hasSize(36);
        assertThat(response.getHeader(RequestIds.HEADER)).isEqualTo(seen);
    }

    @Test
    void replacesUnsafeClientId() throws Exception {
        String seen = runWith("bad id\nwith newline", new MockHttpServletResponse());

        assertThat(seen).hasSize(36).doesNotContain(" ");
    }

    @Test
    void clearsMdcAfterRequest() throws Exception {
        runWith("client-123", new MockHttpServletResponse());

        assertThat(MDC.get(RequestIds.MDC_KEY)).isNull();
    }
}
