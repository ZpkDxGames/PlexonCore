package com.zpkdxgames.plexoncore.api;

import com.zpkdxgames.plexoncore.config.ConfigService;
import com.zpkdxgames.plexoncore.diagnostics.DiagnosticsService.DetailedDiagnostics;
import com.zpkdxgames.plexoncore.diagnostics.DiagnosticsService.DiagnosticsSnapshot;
import com.zpkdxgames.plexoncore.event.CoreEventGateway;
import com.zpkdxgames.plexoncore.gui.GuiService;
import com.zpkdxgames.plexoncore.integration.IntegrationRegistry;
import com.zpkdxgames.plexoncore.item.ItemService;
import com.zpkdxgames.plexoncore.module.ModuleRegistry;
import com.zpkdxgames.plexoncore.origin.BlockOriginService;
import com.zpkdxgames.plexoncore.persistence.SqliteService;
import com.zpkdxgames.plexoncore.scheduler.CoreScheduler;
import com.zpkdxgames.plexoncore.text.TextService;

import java.util.List;

public interface PlexonCoreAPI {
    CoreVersion version();
    ModuleRegistry modules();
    IntegrationRegistry integrations();
    TextService text();
    GuiService gui();
    ItemService items();
    CoreScheduler scheduler();
    SqliteService persistence();
    ConfigService configs();
    DiagnosticsSnapshot diagnostics();

    /**
     * API 2.1 contributor-aware diagnostics. The default preserves binary compatibility for custom
     * API implementations compiled against 2.0; PlexonCore's implementation overrides it with the
     * full contributor set.
     */
    default DetailedDiagnostics detailedDiagnostics() {
        return new DetailedDiagnostics(diagnostics(), List.of());
    }

    /** API 2.0 shared event gateway. Existing API 1.x consumers do not need to call this method. */
    CoreEventGateway events();

    /** API 2.0 authoritative natural/player-placed block origin service. */
    BlockOriginService blockOrigins();

    default boolean supportsApi(int major, int minor) {
        return modules().supportsApi(CoreVersion.of(major, minor, version().pluginVersion()));
    }

    record CoreVersion(int apiMajor, int apiMinor, String pluginVersion) implements Comparable<CoreVersion> {
        public static CoreVersion of(int apiMajor, int apiMinor, String pluginVersion) {
            if (apiMajor < 0 || apiMinor < 0) throw new IllegalArgumentException("API version parts must be non-negative");
            return new CoreVersion(apiMajor, apiMinor, pluginVersion == null ? "unknown" : pluginVersion);
        }

        public String apiVersion() {
            return apiMajor + "." + apiMinor;
        }

        @Override
        public int compareTo(CoreVersion other) {
            int major = Integer.compare(apiMajor, other.apiMajor);
            return major != 0 ? major : Integer.compare(apiMinor, other.apiMinor);
        }
    }
}
