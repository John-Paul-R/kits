package dev.jpcode.kits.config;

import java.nio.file.Path;

import dev.jpcode.kits.KitsMod;

public final class KitsConfig extends Config {

    @ConfigOption
    public final Option<String> starterKit = new Option<>("starter_kit", "", String::valueOf);

    public KitsConfig(Path savePath, String displayName, String documentationLink) {
        super(savePath, displayName, documentationLink);
        starterKit.changeEvent.register(KitsMod::setStarterKit);
    }

}
