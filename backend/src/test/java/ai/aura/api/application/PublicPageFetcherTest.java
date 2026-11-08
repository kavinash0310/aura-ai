package ai.aura.api.application;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicPageFetcherTest {
    @Test
    void acceptsOnlyStandardPortPublicHttpsHostnames() {
        URI accepted = PublicPageFetcher.validatePublicHttpsUrl("https://example.org/a/../report?q=AURA#section");
        assertEquals("https://example.org/report?q=AURA", accepted.toString());

        assertBlocked("http://example.org/page");
        assertBlocked("https://127.0.0.1/admin");
        assertBlocked("https://127.1/admin");
        assertBlocked("https://2130706433/admin");
        assertBlocked("https://[::1]/admin");
        assertBlocked("https://user:secret@example.org/page");
        assertBlocked("https://example.org:8443/page");
        assertBlocked("https://service.localhost/page");
        assertBlocked("https://service.internal/page");
    }

    @Test
    void redirectsMustRemainHttpsOnTheOriginalHostAndStandardPort() {
        URI current = URI.create("https://example.org/research/start");
        assertEquals("https://example.org/research/next", PublicPageFetcher
                .resolveSameHostRedirect(current, "next", "example.org").toString());

        assertBlockedRedirect(current, "https://other.example/page", "example.org");
        assertBlockedRedirect(current, "http://example.org/page", "example.org");
        assertBlockedRedirect(current, "https://example.org:8443/page", "example.org");
    }

    @Test
    void rejectsPrivateAndSpecialIpv4DestinationsButAcceptsPublicAddresses() throws Exception {
        assertFalse(isPublic("0.1.2.3"));
        assertFalse(isPublic("10.0.0.1"));
        assertFalse(isPublic("100.64.0.1"));
        assertFalse(isPublic("127.0.0.1"));
        assertFalse(isPublic("169.254.10.20"));
        assertFalse(isPublic("172.16.0.1"));
        assertFalse(isPublic("192.0.2.10"));
        assertFalse(isPublic("192.168.1.1"));
        assertFalse(isPublic("198.51.100.8"));
        assertFalse(isPublic("203.0.113.8"));
        assertFalse(isPublic("224.0.0.1"));
        assertTrue(isPublic("1.1.1.1"));
        assertTrue(isPublic("93.184.216.34"));
    }

    @Test
    void extractsReadablePageTextWithoutExecutableOrHiddenContentAndCapsOutput() {
        String html = "<html><head><title>Research title</title><style>.hidden{}</style></head>"
                + "<body><nav>menu</nav><main>Useful <b>research</b> text</main>"
                + "<script>stealCredentials()</script><noscript>fallback</noscript></body></html>";
        PublicPageFetcher.ExtractedHtml extracted = PublicPageFetcher.extractHtml(
                html.getBytes(StandardCharsets.UTF_8), "https://example.org/source");

        assertEquals("Research title", extracted.title());
        assertEquals("menu Useful research text", extracted.text());
        assertFalse(extracted.truncated());

        String largeHtml = "<p>" + "A".repeat(PublicPageFetcher.MAX_EXTRACTED_CHARACTERS + 1) + "</p>";
        PublicPageFetcher.ExtractedHtml limited = PublicPageFetcher.extractHtml(
                largeHtml.getBytes(StandardCharsets.UTF_8), "https://example.org/source");
        assertEquals(PublicPageFetcher.MAX_EXTRACTED_CHARACTERS, limited.text().length());
        assertTrue(limited.truncated());
    }

    private static void assertBlocked(String value) {
        PublicPageFetchException exception = assertThrows(PublicPageFetchException.class,
                () -> PublicPageFetcher.validatePublicHttpsUrl(value));
        assertEquals("PUBLIC_PAGE_URL_BLOCKED", exception.getCode());
    }

    private static void assertBlockedRedirect(URI current, String location, String host) {
        PublicPageFetchException exception = assertThrows(PublicPageFetchException.class,
                () -> PublicPageFetcher.resolveSameHostRedirect(current, location, host));
        assertEquals("PUBLIC_PAGE_URL_BLOCKED", exception.getCode());
    }

    private static boolean isPublic(String address) throws Exception {
        return PublicPageFetcher.isPublicIpv4(InetAddress.getByAddress(parseIpv4(address)));
    }

    private static byte[] parseIpv4(String value) {
        String[] parts = value.split("\\.");
        return new byte[]{(byte) Integer.parseInt(parts[0]), (byte) Integer.parseInt(parts[1]),
                (byte) Integer.parseInt(parts[2]), (byte) Integer.parseInt(parts[3])};
    }
}
