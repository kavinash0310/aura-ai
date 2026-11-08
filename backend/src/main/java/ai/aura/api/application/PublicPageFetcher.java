package ai.aura.api.application;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.util.Timeout;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.io.InputStream;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class PublicPageFetcher {
    public static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    public static final int MAX_EXTRACTED_CHARACTERS = 100_000;
    private static final int MAX_REDIRECTS = 2;
    private static final Timeout CONNECT_TIMEOUT = Timeout.ofSeconds(3);
    private static final Timeout RESPONSE_TIMEOUT = Timeout.ofSeconds(5);
    private static final long MAX_FETCH_DURATION_NANOS = Duration.ofSeconds(20).toNanos();
    private static final ThreadPoolExecutor DNS_LOOKUPS = new ThreadPoolExecutor(
            2, 2, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(2), dnsThreadFactory());
    private static final ScheduledThreadPoolExecutor PAGE_DEADLINES = pageDeadlineExecutor();
    private static final Set<String> BLOCKED_SUFFIXES = Set.of(
            ".localhost", ".local", ".internal", ".home", ".lan", ".test", ".invalid", ".example"
    );

    private final CloseableHttpClient client;
    private final Semaphore pageSlot = new Semaphore(1);

    public PublicPageFetcher() {
        DnsResolver publicResolver = new PublicIpv4DnsResolver();
        PoolingHttpClientConnectionManager connections = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(publicResolver)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(CONNECT_TIMEOUT)
                        .setSocketTimeout(RESPONSE_TIMEOUT)
                        .build())
                .setMaxConnTotal(1)
                .setMaxConnPerRoute(1)
                .build();
        this.client = HttpClients.custom()
                .setConnectionManager(connections)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(CONNECT_TIMEOUT)
                        .setResponseTimeout(RESPONSE_TIMEOUT)
                        .build())
                .disableCookieManagement()
                .disableAuthCaching()
                .disableContentCompression()
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .setUserAgent("AURA-Local-Research/0.1")
                .build();
    }

    public FetchedPage fetch(String sourceUrl) {
        if (!pageSlot.tryAcquire()) {
            throw new PublicPageFetchException(HttpStatus.TOO_MANY_REQUESTS, "PUBLIC_PAGE_FETCH_BUSY",
                    "AURA is already reading a page. Wait for it to finish before starting another.");
        }
        try {
            return fetchOne(sourceUrl);
        } finally {
            pageSlot.release();
        }
    }

    @PreDestroy
    public void close() throws IOException {
        client.close();
    }

    private FetchedPage fetchOne(String sourceUrl) {
        URI current = validatePublicHttpsUrl(sourceUrl);
        String initialHost = current.getHost().toLowerCase(Locale.ROOT);
        long deadlineNanos = System.nanoTime() + MAX_FETCH_DURATION_NANOS;
        try {
            for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) throw unavailable();
                HttpGet request = new HttpGet(current);
                request.setHeader("Accept", "text/html, application/xhtml+xml;q=0.9");
                request.setHeader("Accept-Encoding", "identity");
                ScheduledFuture<?> hardTimeout = PAGE_DEADLINES.schedule(request::cancel,
                        remainingNanos, TimeUnit.NANOSECONDS);
                PageResponse response;
                try {
                    response = client.execute(request, responseHandler());
                    if (hardTimeout.isDone()) throw unavailable();
                } finally {
                    hardTimeout.cancel(false);
                }
                if (isRedirect(response.status())) {
                    if (redirects == MAX_REDIRECTS || response.location() == null || response.location().isBlank()) {
                        throw unavailable();
                    }
                    current = resolveSameHostRedirect(current, response.location(), initialHost);
                    continue;
                }
                if (response.status() != 200) throw unavailable();
                if (!isHtml(response.contentType())) {
                    throw new PublicPageFetchException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PUBLIC_PAGE_NOT_HTML",
                            "This source did not return an HTML page.");
                }
                if (response.contentEncoding() != null
                        && !response.contentEncoding().isBlank()
                        && !response.contentEncoding().equalsIgnoreCase("identity")) {
                    throw unavailable();
                }
                if (response.body().length > MAX_RESPONSE_BYTES) throw tooLarge();

                ExtractedHtml extracted = extractHtml(response.body(), current.toString());
                return new FetchedPage(current.toString(), current.getHost(), extracted.title(), extracted.text(),
                        extracted.truncated(), Instant.now());
            }
            throw unavailable();
        } catch (PublicPageFetchException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw unavailable();
        }
    }

    public static URI validatePublicHttpsUrl(String value) {
        if (value == null || value.length() > 2048) throw blockedUrl();
        try {
            URI uri = new URI(value).normalize();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                    || uri.getHost() == null || (uri.getPort() != -1 && uri.getPort() != 443)) {
                throw blockedUrl();
            }
            String host = IDN.toASCII(uri.getHost(), IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            if (!isAllowedHostname(host)) throw blockedUrl();
            StringBuilder canonical = new StringBuilder("https://").append(host);
            String path = uri.getRawPath();
            canonical.append(path == null || path.isEmpty() ? "/" : path);
            if (uri.getRawQuery() != null) canonical.append('?').append(uri.getRawQuery());
            return URI.create(canonical.toString());
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw blockedUrl();
        }
    }

    static URI resolveSameHostRedirect(URI current, String location, String initialHost) {
        URI resolved;
        try {
            resolved = current.resolve(new URI(location));
        } catch (IllegalArgumentException | URISyntaxException exception) {
            throw unavailable();
        }
        URI checked = validatePublicHttpsUrl(resolved.toString());
        if (!initialHost.equalsIgnoreCase(checked.getHost())) throw blockedUrl();
        return checked;
    }

    static boolean isPublicIpv4(InetAddress address) {
        if (!(address instanceof Inet4Address) || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] octets = address.getAddress();
        int first = Byte.toUnsignedInt(octets[0]);
        int second = Byte.toUnsignedInt(octets[1]);
        int third = Byte.toUnsignedInt(octets[2]);
        return first != 0 && first < 224
                && !(first == 100 && second >= 64 && second <= 127)
                && !(first == 192 && (second == 0 || (second == 88 && third == 99)))
                && !(first == 192 && second == 0 && third == 2)
                && !(first == 198 && (second == 18 || second == 19 || (second == 51 && third == 100)))
                && !(first == 203 && second == 0 && third == 113);
    }

    static ExtractedHtml extractHtml(byte[] html, String baseUri) {
        Document page = Jsoup.parse(html, null, baseUri);
        page.select("script,style,noscript,template,svg,iframe,object,embed").remove();
        String text = page.body().text().replaceAll("\\s+", " ").strip();
        if (text.isBlank()) throw unavailable();
        boolean truncated = text.length() > MAX_EXTRACTED_CHARACTERS;
        if (truncated) text = text.substring(0, MAX_EXTRACTED_CHARACTERS);
        String title = page.title().strip();
        if (title.length() > 300) title = title.substring(0, 300);
        return new ExtractedHtml(title, text, truncated);
    }

    private static boolean isAllowedHostname(String host) {
        if (host.length() > 253 || !host.contains(".") || host.matches("[0-9.]+")) return false;
        for (String suffix : BLOCKED_SUFFIXES) {
            if (host.equals(suffix.substring(1)) || host.endsWith(suffix)) return false;
        }
        for (String label : host.split("\\.")) {
            if (label.isEmpty() || label.length() > 63
                    || !label.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) return false;
        }
        return true;
    }

    private static ThreadFactory dnsThreadFactory() {
        return task -> {
            Thread thread = new Thread(task, "aura-public-dns");
            thread.setDaemon(true);
            return thread;
        };
    }

    private static ScheduledThreadPoolExecutor pageDeadlineExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "aura-public-page-deadline");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    private static HttpClientResponseHandler<PageResponse> responseHandler() {
        return response -> {
            String location = header(response, "Location");
            String contentType = header(response, "Content-Type");
            String contentEncoding = header(response, "Content-Encoding");
            HttpEntity entity = response.getEntity();
            byte[] body = new byte[0];
            if (entity != null) {
                long contentLength = entity.getContentLength();
                if (contentLength > MAX_RESPONSE_BYTES) throw tooLarge();
                try (InputStream input = entity.getContent()) {
                    body = input.readNBytes(MAX_RESPONSE_BYTES + 1);
                }
                if (body.length > MAX_RESPONSE_BYTES) throw tooLarge();
            }
            return new PageResponse(response.getCode(), location, contentType, contentEncoding, body);
        };
    }

    private static String header(ClassicHttpResponse response, String name) {
        var header = response.getFirstHeader(name);
        return header == null ? null : header.getValue();
    }

    private static boolean isHtml(String contentType) {
        if (contentType == null) return false;
        String mediaType = contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        return mediaType.equals("text/html") || mediaType.equals("application/xhtml+xml");
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static PublicPageFetchException blockedUrl() {
        return new PublicPageFetchException(HttpStatus.BAD_REQUEST, "PUBLIC_PAGE_URL_BLOCKED",
                "Page reading only allows HTTPS pages on public hostnames and the standard HTTPS port.");
    }

    private static PublicPageFetchException tooLarge() {
        return new PublicPageFetchException(HttpStatus.PAYLOAD_TOO_LARGE, "PUBLIC_PAGE_TOO_LARGE",
                "The page exceeded AURA's 1 MB response limit.");
    }

    private static PublicPageFetchException unavailable() {
        return new PublicPageFetchException(HttpStatus.BAD_GATEWAY, "PUBLIC_PAGE_FETCH_FAILED",
                "AURA could not read this public page. It may block automated requests or return unsupported content.");
    }

    public record FetchedPage(String url, String host, String title, String text, boolean truncated, Instant fetchedAt) { }

    record ExtractedHtml(String title, String text, boolean truncated) { }

    private record PageResponse(int status, String location, String contentType, String contentEncoding, byte[] body) { }

    private static final class PublicIpv4DnsResolver extends SystemDefaultDnsResolver {
        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            Future<InetAddress[]> lookup;
            try {
                lookup = DNS_LOOKUPS.submit(() -> super.resolve(host));
            } catch (RuntimeException exception) {
                throw new UnknownHostException("Public DNS lookup is busy");
            }
            InetAddress[] resolved;
            try {
                resolved = lookup.get(3, TimeUnit.SECONDS);
            } catch (TimeoutException exception) {
                cancelLookup(lookup);
                throw new UnknownHostException("Public DNS lookup timed out");
            } catch (InterruptedException exception) {
                cancelLookup(lookup);
                Thread.currentThread().interrupt();
                throw new UnknownHostException("Public DNS lookup was interrupted");
            } catch (ExecutionException exception) {
                throw new UnknownHostException("Public DNS lookup failed");
            }
            if (resolved == null || resolved.length == 0) throw new UnknownHostException(host);
            List<InetAddress> ipv4Addresses = Arrays.stream(resolved)
                    .filter(Inet4Address.class::isInstance)
                    .toList();
            if (ipv4Addresses.isEmpty() || ipv4Addresses.stream().anyMatch(address -> !isPublicIpv4(address))) {
                throw new UnknownHostException("Non-public address rejected");
            }
            return ipv4Addresses.toArray(InetAddress[]::new);
        }

        private static void cancelLookup(Future<InetAddress[]> lookup) {
            lookup.cancel(true);
            if (lookup instanceof Runnable task) DNS_LOOKUPS.remove(task);
        }

        @Override
        public String resolveCanonicalHostname(String host) {
            return host;
        }
    }
}
