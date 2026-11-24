package io.github.railgun19457.astrbotadapter.paper;

import com.google.gson.JsonObject;

import java.lang.reflect.Method;
import java.util.logging.Logger;

record FoliaMetrics(Method globalTick, Method report, Method tpsData, Method msptData, Method segment, Method average) {
    static FoliaMetrics open(ClassLoader loader, Logger logger) {
        try {
            var server = Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false, loader);
            var globalTick = server.getMethod("getGlobalTickData");
            var report = globalTick.getReturnType().getMethod("getTickReport5s", long.class);
            var tpsData = report.getReturnType().getMethod("tpsData");
            var msptData = report.getReturnType().getMethod("timePerTickData");
            var segment = tpsData.getReturnType().getMethod("segmentAll");
            return new FoliaMetrics(globalTick, report, tpsData, msptData, segment, segment.getReturnType().getMethod("average"));
        } catch (ReflectiveOperationException | LinkageError failure) {
            logger.info("This Folia build does not expose the global region tick report");
            return null;
        }
    }

    void addTo(JsonObject status) {
        try {
            Object sample = report.invoke(globalTick.invoke(null), System.nanoTime());
            if (sample != null) {
                double tps = (double) average.invoke(segment.invoke(tpsData.invoke(sample)));
                double mspt = (double) average.invoke(segment.invoke(msptData.invoke(sample))) / 1_000_000;
                if (Double.isFinite(tps) && Double.isFinite(mspt)) {
                    status.addProperty("tps", Math.min(20, tps));
                    status.addProperty("mspt", mspt);
                    status.addProperty("metrics_scope", "global_region");
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Server startup or a different Folia build may have no complete global sample.
        }
    }
}
