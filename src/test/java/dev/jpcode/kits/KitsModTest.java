package dev.jpcode.kits;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Kits")
public class KitsModTest
{
    @Test
    @DisplayName("A logger is available")
    @Disabled("for demonstration purposes only")
    void shouldHaveLogger()
    {
        assertNotNull(KitsMod.LOGGER);
    }
}
