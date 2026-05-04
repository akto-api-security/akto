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
        final AtomicLong callCount   = new AtomicLong(0);
        final AtomicLong inputBytes  = new AtomicLong(0);
        final AtomicLong outputBytes = new AtomicLong(0);
    }

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
            String key = (accountId != null ? accountId : 0) + "||" + httpReq.getRequestURI();
            long inputSz  = Math.max(0, httpReq.getContentLength());
            long outputSz = wrapper.getByteCount();

            BandwidthEntry entry = map.computeIfAbsent(key, k -> new BandwidthEntry());
            entry.callCount.incrementAndGet();
            entry.inputBytes.addAndGet(inputSz);
            entry.outputBytes.addAndGet(outputSz);
        }
    }

    private void printSummary(ConcurrentHashMap<String, BandwidthEntry> snapshot) {
        if (snapshot.isEmpty()) return;

        System.out.println("[BandwidthSummary] ===== 5-minute window =====");
        snapshot.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().outputBytes.get(), a.getValue().outputBytes.get()))
            .forEach(e -> {
                String[] parts = e.getKey().split("\\|\\|", 2);
                System.out.printf("[BandwidthSummary] account=%-12s api=%-60s calls=%-8d in=%-12d out=%d%n",
                    parts[0], parts.length > 1 ? parts[1] : "",
                    e.getValue().callCount.get(),
                    e.getValue().inputBytes.get(),
                    e.getValue().outputBytes.get());
            });
        System.out.println("[BandwidthSummary] ===========================");
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
