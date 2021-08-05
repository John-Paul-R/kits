package dev.jpcode.kits.config;

@FunctionalInterface
public interface ValueParser<T> {
    T parseValue(String value);
}
