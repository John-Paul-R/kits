package dev.jpcode.kits.config;

import java.nio.file.Path;

import dev.jpcode.kits.KitsMod;

public final class KitsConfig extends Config {

    @ConfigOption public final Option<String> starterKit = new Option<>("starter_kit", "", String::valueOf);
    @ConfigOption public final Option<String> mysqlDatabase = new Option<>("mysql_database", "kits", String::valueOf);
    @ConfigOption public final Option<String> mysqlUrl = new Option<>("mysql_url", "", String::valueOf);
    @ConfigOption public final Option<String> mysqlUser = new Option<>("mysql_user", "", String::valueOf);
    @ConfigOption public final Option<String> mysqlPassword = new Option<>("mysql_password", "", String::valueOf);

    public KitsConfig(Path savePath, String displayName, String documentationLink) {
        super(savePath, displayName, documentationLink);
        starterKit.changeEvent.register(KitsMod::setStarterKit);
    }

}
