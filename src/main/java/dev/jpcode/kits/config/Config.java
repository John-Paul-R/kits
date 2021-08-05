package dev.jpcode.kits.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import dev.jpcode.kits.KitsMod;

import static dev.jpcode.kits.KitsMod.LOGGER;

public final class Config {

    private Config() {}

    private static SortedProperties props;
    private static final String CONFIG_PATH = "./config/kits.properties";

    @ConfigOption
    public static final Option<String> STARTER_KIT = new Option<>("starter_kit", "", String::valueOf);

    static {
        STARTER_KIT.changeEvent.register(KitsMod::setStarterKit);
    }

    public static void loadOrCreateProperties() {
        props = new SortedProperties();
        File inFile = new File(CONFIG_PATH);

        try {
            boolean fileAlreadyExisted = !inFile.createNewFile();
            if (fileAlreadyExisted) {
                props.load(new FileReader(inFile));
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to load preferences.");
        }
        initProperties();
        storeProperties();
    }

    private static void initProperties() {
        // Cursed reflection reloading of all properties.
        Arrays.stream(Config.class.getDeclaredFields())
            .filter(field -> field.isAnnotationPresent(ConfigOption.class))
            .forEach(field -> {
                try {
                    ((Option<?>) field.get(Config.class)).loadAndSave(props);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            });
    }

    public static void storeProperties() {
        try {
            File outFile = new File(CONFIG_PATH);
            FileWriter writer = new FileWriter(outFile);

            props.storeSorted(writer, new StringBuilder()
                .append("Essential Commands Properties\n")
                .append("Config Documentation: https://github.com/John-Paul-R/Essential-Commands/wiki/Config-Documentation")
                .toString()
            );
        } catch (IOException e) {
            LOGGER.warn("Failed to store preferences to disk.");
        }

    }

    static final Style DEFAULT_STYLE = Style.EMPTY.withFormatting(Formatting.GOLD);
    static final Style ACCENT_STYLE = Style.EMPTY.withFormatting(Formatting.GREEN);

    public static @NotNull Text stateAsText() {
        LiteralText result = new LiteralText("");
        String newLine = "\n";//System.getProperty("line.separator");

        result.append(new LiteralText("Kits Config {").setStyle(DEFAULT_STYLE));
        result.append(newLine);
        LiteralText propsText = new LiteralText("");
        result.append(propsText);

        //print field names paired with their values
        for (Field field : PUBLIC_FIELDS) {
            try {
                if (Modifier.isPublic(field.getModifiers())) {
                    propsText.append(fieldAsText(field).append(newLine));
                }
            } catch (IllegalAccessException ex) {
                ex.printStackTrace();
            }
        }
        result.append(new LiteralText("}").setStyle(ACCENT_STYLE));

        return result;

    }

    private static final List<String> PUBLIC_FIELD_NAMES;
    private static final List<Field> PUBLIC_FIELDS;

    static {
        PUBLIC_FIELD_NAMES = Arrays.stream(Config.class.getDeclaredFields())
            .filter(field -> Modifier.isPublic(field.getModifiers()))
            .map(Field::getName)
            .sorted()
            .collect(Collectors.toList());
        PUBLIC_FIELDS = Arrays.stream(Config.class.getDeclaredFields())
            .filter(field -> Modifier.isPublic(field.getModifiers()))
            .sorted(Comparator.comparing(Field::getName))
            .collect(Collectors.toList());

    }

    public static List<String> getPublicFieldNames() {
        return PUBLIC_FIELD_NAMES;
    }

    private static MutableText fieldAsText(Field field) throws IllegalAccessException {
        return new LiteralText("")
            .append(new LiteralText(field.getName() + ": ").setStyle(DEFAULT_STYLE))
            .append(new LiteralText(field.get(Config.class).toString()));
    }

    public static @Nullable MutableText getFieldValueAsText(String fieldName) throws NoSuchFieldException {
        try {
            return fieldAsText(Config.class.getField(fieldName));
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

}
