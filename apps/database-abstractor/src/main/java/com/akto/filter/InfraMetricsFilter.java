package com.akto.filter;

import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.CyborgMetrics;
import com.akto.metrics.CyborgMetricsConfig;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Records per-request Prometheus metrics for /api/* traffic: count, latency, response size, and
 * in-flight concurrency. Gathers the tag values (uri, method, status, account_id) and hands the measurement
 * to {@link CyborgMetrics} — all metric definitions live there.
 *
 * The Servlet 2.5 API on this service's classpath has no HttpServletResponse.getStatus(), so the
 * status code is captured via a response wrapper (the standard pre-3.0 pattern). The same wrapper
 * counts response body bytes, since 2.5 also has no getContentLength().
 */
public class InfraMetricsFilter implements Filter {

    private static final LoggerMaker loggerMaker = new LoggerMaker(InfraMetricsFilter.class, LogDb.DB_ABS);

    /**
     * Captures the HTTP status set downstream (defaults to 200 when nothing sets it) and the response
     * body size. The size comes from setContentLength when the app declares it (Struts' JSONUtil does,
     * with the exact encoded byte count), so the common path does no per-byte work; only responses
     * without a Content-Length (gzip/streamed) are counted as they're written.
     *
     * Neither the stream nor the writer buffers anything of its own: every write goes straight to the
     * container, so flush/reset/sendError behave exactly as unwrapped (Struts' JSONUtil never flushes
     * its writer; the container does at end of request).
     */
    private static class StatusCapturingResponse extends HttpServletResponseWrapper {
        private int status = HttpServletResponse.SC_OK;
        private long bytesWritten = 0;
        private long contentLength = -1; // -1 = not declared; count bytes as they're written
        private ServletOutputStream countingStream;
        private PrintWriter countingWriter;

        StatusCapturingResponse(HttpServletResponse response) {
            super(response);
        }

        @Override public void setStatus(int sc) { this.status = sc; super.setStatus(sc); }

        @Override
        @SuppressWarnings("deprecation")
        public void setStatus(int sc, String sm) { this.status = sc; super.setStatus(sc, sm); }

        @Override public void sendError(int sc) throws IOException { this.status = sc; this.contentLength = -1; super.sendError(sc); }

        @Override public void sendError(int sc, String msg) throws IOException { this.status = sc; this.contentLength = -1; super.sendError(sc, msg); }

        @Override public void sendRedirect(String location) throws IOException { this.status = HttpServletResponse.SC_FOUND; super.sendRedirect(location); }

        @Override public void setContentLength(int len) { this.contentLength = len; super.setContentLength(len); }

        @Override public void reset() { this.contentLength = -1; this.bytesWritten = 0; super.reset(); }

        @Override public void resetBuffer() { this.bytesWritten = 0; super.resetBuffer(); }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (countingStream == null) {
                final ServletOutputStream delegate = super.getOutputStream();
                countingStream = new ServletOutputStream() {
                    @Override public void write(int b) throws IOException { delegate.write(b); if (contentLength < 0) bytesWritten++; }
                    @Override public void write(byte[] b, int off, int len) throws IOException { delegate.write(b, off, len); if (contentLength < 0) bytesWritten += len; }
                    @Override public void flush() throws IOException { delegate.flush(); }
                    @Override public void close() throws IOException { delegate.close(); }
                };
            }
            return countingStream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (countingWriter == null) {
                countingWriter = new CountingPrintWriter(super.getWriter(), getCharacterEncoding());
            }
            return countingWriter;
        }

        int getCapturedStatus() { return status; }

        /** Declared Content-Length when set, otherwise the bytes counted while writing. */
        long getBytesWritten() { return contentLength >= 0 ? contentLength : bytesWritten; }

        /**
         * Pass-through writer over the container's writer that counts the bytes the chars will encode
         * to. UTF-8 and single-byte charsets are counted by scanning chars in place (no per-write
         * allocation); any other charset falls back to encoding the chunk.
         */
        private final class CountingPrintWriter extends PrintWriter {
            private final boolean utf8;
            private final Charset otherCharset; // null for UTF-8 and single-byte charsets
            private boolean pendingHighSurrogate;

            CountingPrintWriter(PrintWriter delegate, String encoding) {
                super(delegate);
                Charset charset;
                try {
                    charset = Charset.forName(encoding == null ? "ISO-8859-1" : encoding); // servlet default
                } catch (Exception e) {
                    charset = StandardCharsets.UTF_8;
                }
                this.utf8 = StandardCharsets.UTF_8.equals(charset);
                boolean singleByte = StandardCharsets.ISO_8859_1.equals(charset) || StandardCharsets.US_ASCII.equals(charset);
                this.otherCharset = (utf8 || singleByte) ? null : charset;
            }

            @Override
            public void write(int c) {
                super.write(c);
                if (contentLength >= 0) return;
                if (otherCharset != null) {
                    bytesWritten += String.valueOf((char) c).getBytes(otherCharset).length;
                } else {
                    count((char) c);
                }
            }

            @Override
            public void write(char[] buf, int off, int len) {
                super.write(buf, off, len);
                if (contentLength >= 0) return;
                if (otherCharset != null) {
                    bytesWritten += new String(buf, off, len).getBytes(otherCharset).length;
                    return;
                }
                for (int i = off; i < off + len; i++) count(buf[i]);
            }

            @Override
            public void write(String str, int off, int len) {
                super.write(str, off, len);
                if (contentLength >= 0) return;
                if (otherCharset != null) {
                    bytesWritten += str.substring(off, off + len).getBytes(otherCharset).length;
                    return;
                }
                for (int i = off; i < off + len; i++) count(str.charAt(i));
            }

            // PrintWriter.println() writes the separator straight to the delegate; route it through write().
            @Override
            public void println() {
                write(System.lineSeparator());
            }

            private void count(char c) {
                if (!utf8) {
                    bytesWritten++;
                    return;
                }
                if (pendingHighSurrogate) {
                    pendingHighSurrogate = false;
                    if (Character.isLowSurrogate(c)) return; // pair already counted as 4 bytes
                }
                if (c < 0x80) {
                    bytesWritten += 1;
                } else if (c < 0x800) {
                    bytesWritten += 2;
                } else if (Character.isHighSurrogate(c)) {
                    bytesWritten += 4;
                    pendingHighSurrogate = true;
                } else {
                    bytesWritten += 3;
                }
            }
        }
    }

    @Override
    public void init(FilterConfig filterConfig) { }

    @Override
    public void destroy() { }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {
        // Feature off -> zero-overhead passthrough (no wrapping, no recording).
        if (!CyborgMetricsConfig.isEnabled() || !(response instanceof HttpServletResponse)) {
            filterChain.doFilter(request, response);
            return;
        }

        StatusCapturingResponse wrapped = new StatusCapturingResponse((HttpServletResponse) response);
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, wrapped);
        } finally {
            long duration = System.currentTimeMillis() - start;
            try {
                HttpServletRequest httpServletRequest = (HttpServletRequest) request;

                String uri = httpServletRequest.getRequestURI();             // bounded /api/<action> set
                String method = httpServletRequest.getMethod();
                String status = String.valueOf(wrapped.getCapturedStatus()); // actual HTTP status code
                Integer acc = Context.accountId.get();                       // set by AuthFilter upstream
                String accountId = acc != null ? String.valueOf(acc) : CyborgMetrics.UNKNOWN;

                CyborgMetrics.recordHttpRequest(uri, method, status, accountId, duration);
                long responseBytes = wrapped.getBytesWritten();
                if (responseBytes > 0) { // 0 = no app-written body (container error page etc.); don't skew the size histogram
                    CyborgMetrics.recordHttpResponseSize(uri, method, status, responseBytes);
                }
            } catch (Throwable t) { // metrics must never fail the request, not even on an Error
                loggerMaker.errorAndAddToDb(String.format("InfraMetricsFilter error: %s", t), LogDb.DB_ABS);
            }
        }
    }
}
