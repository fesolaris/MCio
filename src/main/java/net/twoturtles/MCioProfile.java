package net.twoturtles;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Opt-in per-phase timers, enabled with MCIO_PROFILE=true. Counters are nanoseconds per phase,
 * reported as one parseable line per phase every report interval, then reset. The disabled path is
 * a single static boolean read, so instrumented sites can be left in place.
 */
public final class MCioProfile {
  public enum Phase {
    CLIENT_TICK,
    ACTION_RECV,
    ACTION_PROCESS,
    CAPTURE_ALLOC,
    CAPTURE_READ,
    CAPTURE_CALLBACK,
    OBS_COLLECT,
    OBS_PACK,
    OBS_SEND,
    SERVER_GATE,
    SERVER_IDLE,
    SERVER_TICK,
    SERVER_PUSH
  }

  private static final Logger LOGGER = LogUtils.getLogger();
  private static final long REPORT_INTERVAL_NS = 2_000_000_000L;
  private static final Phase[] PHASES = Phase.values();
  private static final long[] TOTAL_NS = new long[PHASES.length];
  private static final long[] COUNT = new long[PHASES.length];
  private static final long[] MAX_NS = new long[PHASES.length];

  private static volatile boolean initialized = false;
  private static boolean on = false;
  private static long nextReportNs = 0;

  private MCioProfile() {}

  public static boolean enabled() {
    return ensureInit();
  }

  private static boolean ensureInit() {
    if (!initialized) {
      on = MCioConfig.getInstance().profile;
      initialized = true;
      LOGGER.info("MCio-Profile enabled={}", on);
    }
    return on;
  }

  public static long t() {
    return System.nanoTime();
  }

  public static void add(Phase phase, long startNs) {
    if (!ensureInit()) {
      return;
    }
    long dt = System.nanoTime() - startNs;
    int i = phase.ordinal();
    TOTAL_NS[i] += dt;
    COUNT[i] += 1;
    if (dt > MAX_NS[i]) {
      MAX_NS[i] = dt;
    }
  }

  public static void tick() {
    if (!ensureInit()) {
      return;
    }
    long now = System.nanoTime();
    if (nextReportNs == 0) {
      nextReportNs = now + REPORT_INTERVAL_NS;
      return;
    }
    if (now < nextReportNs) {
      return;
    }
    nextReportNs = now + REPORT_INTERVAL_NS;
    for (Phase phase : PHASES) {
      int i = phase.ordinal();
      long count = COUNT[i];
      if (count == 0) {
        continue;
      }
      LOGGER.info(
          "PROFILE phase={} n={} mean_us={} max_us={}",
          phase,
          count,
          String.format("%.1f", TOTAL_NS[i] / 1000.0 / count),
          String.format("%.1f", MAX_NS[i] / 1000.0));
      TOTAL_NS[i] = 0;
      COUNT[i] = 0;
      MAX_NS[i] = 0;
    }
  }
}
