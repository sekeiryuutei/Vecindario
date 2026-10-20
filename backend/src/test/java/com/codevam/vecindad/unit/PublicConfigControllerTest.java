package com.codevam.vecindad.unit;

import com.codevam.vecindad.bootstrap.PublicConfigController;
import com.codevam.vecindad.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicConfigControllerTest {

    @Test
    @SuppressWarnings("unchecked")
    void manifestUsesConfiguredBrandAndPwaMetadata() {
        AppProperties props = mock(AppProperties.class);
        when(props.name()).thenReturn("Mi Copropiedad");
        when(props.company()).thenReturn("Administradora");

        PublicConfigController controller = new PublicConfigController(props);
        ResponseEntity<Map<String, Object>> response = controller.manifest();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(MediaType.parseMediaType("application/manifest+json"), response.getHeaders().getContentType());
        assertNotNull(response.getHeaders().getCacheControl());
        assertTrue(response.getHeaders().getCacheControl().contains("no-cache"));

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("Mi Copropiedad", body.get("name"));
        assertEquals("Mi Copropiedad", body.get("short_name"));
        assertEquals("/", body.get("start_url"));
        assertEquals("/", body.get("scope"));
        assertEquals("standalone", body.get("display"));

        List<Map<String, String>> icons = (List<Map<String, String>>) body.get("icons");
        assertEquals(3, icons.size());
        assertTrue(icons.stream().anyMatch(icon -> "maskable".equals(icon.get("purpose"))));
    }

    @Test
    void publicConfigExposesNameAndCompany() {
        AppProperties props = mock(AppProperties.class);
        when(props.name()).thenReturn("Mi Copropiedad");
        when(props.company()).thenReturn("Administradora");

        PublicConfigController.PublicConfig config = new PublicConfigController(props).config();

        assertEquals("Mi Copropiedad", config.appName());
        assertEquals("Administradora", config.company());
    }
}
