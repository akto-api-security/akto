package com.akto.threat.backend;

import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class HealthCheckServer {
  public static final int PORT = 9090;

  public static void startHttpServer() throws Exception {

    Server server = new Server(PORT); // HTTP server port

    // Create and configure the servlet handler
    ServletHandler handler = new ServletHandler();
    server.setHandler(handler);

    // Define a simple servlet to handle HTTP requests
    handler.addServletWithMapping(
        new ServletHolder(
            new HttpServlet() {
              @Override
              protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                  throws IOException {
                resp.getWriter().println("OK");
              }
            }),
        "/");

    // Start the HTTP server
    server.start();
    System.out.println("HTTP Server started on port " + PORT);
    server.join();
  }
}
