package com.akto.filter;

import com.akto.dao.context.Context;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class BandwidthTrackingFilter implements Filter {

    static class BandwidthEntry {
        final AtomicLong callCount     = new AtomicLong(0);
        final AtomicLong inputBytes    = new AtomicLong(0);
        final AtomicLong outputBytes   = new AtomicLong(0);
        final AtomicLong maxInputBytes = new AtomicLong(0);
        /**
         * Requests that arrived without a Content-Length (chunked transfer-encoding). Their bodies
         * are NOT counted in inputBytes, so a non-zero value here means inputBytes is an undercount
         * for this bucket and the numbers should not be trusted on their own.
         */
        final AtomicLong unknownLengthCount = new AtomicLong(0);
    }

    /** Buckets printed per window, highest inbound bytes first. Caps log volume on a wide fleet. */
    private static final int TOP_N = 50;

    private static volatile ConcurrentHashMap<String, BandwidthEntry> map = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Override
    public void init(FilterConfig config) throws ServletException {
        scheduler.scheduleAtFixedRate(() -> {
            ConcurrentHashMap<String, BandwidthEntry> snapshot = map;
            map = new ConcurrentHashMap<>();
            printSummary(snapshot);
        }, 5, 5, TimeUnit.MINUTES);
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        HttpServletResponse httpRes = (HttpServletResponse) res;

        CountingResponseWrapper wrapper = new CountingResponseWrapper(httpRes);
        try {
            chain.doFilter(httpReq, wrapper);
        } finally {
            Integer accountId = Context.accountId.get();
            String encoding = normalizeEncoding(httpReq.getHeader("content-encoding"));
            String key = (accountId != null ? accountId : 0) + "||" + httpReq.getRequestURI() + "||" + encoding;

            // Content-Length is the size on the wire (already compressed when Content-Encoding is
            // set), which is what the Application Gateway meters. It is -1 for chunked requests;
            // those are counted separately rather than silently folded in as zero.
            int declaredLen = httpReq.getContentLength();
            long inputSz  = Math.max(0, declaredLen);
            long outputSz = wrapper.getByteCount();

            BandwidthEntry entry = map.computeIfAbsent(key, k -> new BandwidthEntry());
            entry.callCount.incrementAndGet();
            entry.inputBytes.addAndGet(inputSz);
            entry.outputBytes.addAndGet(outputSz);
            if (declaredLen < 0) {
                entry.unknownLengthCount.incrementAndGet();
            }
            recordMax(entry.maxInputBytes, inputSz);
        }
    }

    private void printSummary(ConcurrentHashMap<String, BandwidthEntry> snapshot) {
        if (snapshot.isEmpty()) return;

        System.out.println("[BandwidthSummary] ===== 5-minute window (top " + TOP_N + " by inbound bytes) =====");
        System.out.println("[BandwidthSummary] avgIn/maxIn are bytes per call; enc is the request's "
            + "Content-Encoding; noLen counts chunked requests whose body is not included in 'in'.");
        snapshot.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().inputBytes.get(), a.getValue().inputBytes.get()))
            .limit(TOP_N)
            .forEach(e -> {
                String[] parts = e.getKey().split("\\|\\|", 3);
                BandwidthEntry v = e.getValue();
                long calls = v.callCount.get();
                long in = v.inputBytes.get();
                System.out.printf(
                    "[BandwidthSummary] account=%-10s enc=%-9s api=%-55s calls=%-8d in=%-14d avgIn=%-10d maxIn=%-10d out=%-14d noLen=%d%n",
                    parts[0],
                    parts.length > 2 ? parts[2] : "none",
                    parts.length > 1 ? parts[1] : "",
                    calls, in,
                    calls > 0 ? in / calls : 0,
                    v.maxInputBytes.get(),
                    v.outputBytes.get(),
                    v.unknownLengthCount.get());
            });
        System.out.println("[BandwidthSummary] ===========================");
    }

    /** Raises {@code target} to {@code candidate} if it is larger. Lock-free; retries on contention. */
    private static void recordMax(AtomicLong target, long candidate) {
        long prev = target.get();
        while (candidate > prev && !target.compareAndSet(prev, candidate)) {
            prev = target.get();
        }
    }

    /**
     * Collapses the client-supplied Content-Encoding to a small fixed set. The header is attacker-
     * controlled and becomes part of the map key, so anything unrecognised folds into "other" —
     * without this, arbitrary header values would grow the map unbounded.
     */
    private static String normalizeEncoding(String headerValue) {
        if (headerValue == null) return "none";
        String v = headerValue.trim().toLowerCase(java.util.Locale.ROOT);
        if (v.isEmpty()) return "none";
        switch (v) {
            case "gzip":
            case "x-gzip":
            case "deflate":
            case "br":
            case "zstd":
            case "identity":
                return v;
            default:
                return "other";
        }
    }

    static class CountingResponseWrapper extends HttpServletResponseWrapper {
        private CountingOutputStream cos;
        private PrintWriter countingWriter;
        private long writerByteCount = 0;

        CountingResponseWrapper(HttpServletResponse res) {
            super(res);
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (cos == null) {
                cos = new CountingOutputStream(super.getOutputStream());
            }
            return cos;
        }

        // Struts2 JSON result writes via getWriter(), not getOutputStream()
        @Override
        public PrintWriter getWriter() throws IOException {
            if (countingWriter == null) {
                countingWriter = new PrintWriter(super.getWriter()) {
                    @Override public void write(int c)                        { super.write(c);          writerByteCount++; }
                    @Override public void write(char[] buf, int off, int len) { super.write(buf,off,len); writerByteCount += len; }
                    @Override public void write(String s, int off, int len)   { super.write(s,off,len);   writerByteCount += len; }
                };
            }
            return countingWriter;
        }

        long getByteCount() {
            return (cos != null ? cos.count : 0) + writerByteCount;
        }
    }

    static class CountingOutputStream extends ServletOutputStream {
        private final ServletOutputStream wrapped;
        long count = 0;

        CountingOutputStream(ServletOutputStream wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public void write(int b) throws IOException {
            wrapped.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            wrapped.write(b, off, len);
            count += len;
        }

    }

    @Override
    public void destroy() {
        scheduler.shutdown();
    }
}
