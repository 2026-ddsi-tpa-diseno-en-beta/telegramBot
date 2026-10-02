package ar.edu.utn.dds.bot;
import org.slf4j.*;
final class ClientEvents {
  static void completed(String destination, int status, String trace, long start) {
    var previous = MDC.getCopyOfContextMap();
    try {
      MDC.put("component", "telegram"); MDC.put("traceId", trace);
      MDC.put("event", "telegram.integracion"); MDC.put("destination", destination);
      MDC.put("status", String.valueOf(status)); MDC.put("durationMs", String.valueOf((System.nanoTime()-start)/1_000_000));
      MDC.put("outcome", status >= 200 && status < 300 ? "ok" : "error");
      LoggerFactory.getLogger(ClientEvents.class).info("telegram.integracion destino={} status={}", destination, status);
    } finally { MDC.clear(); if (previous != null) MDC.setContextMap(previous); }
  }
}
