package ieti.jobswipe.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

class RestAuthenticationEntryPointTest {

    @Test
    void shouldWriteUnauthorizedJsonResponse() throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("Invalid token"));

        assertEquals(401, response.getStatus());
        assertEquals(APPLICATION_JSON_VALUE, response.getContentType());
        assertEquals("{\"error\":\"Invalid token\"}", response.getContentAsString());
    }
}

