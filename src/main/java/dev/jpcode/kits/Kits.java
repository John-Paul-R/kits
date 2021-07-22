package dev.jpcode.kits;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.api.ModInitializer;

public class Kits implements ModInitializer
{
    public static final Logger LOGGER = LogManager.getLogger("kits");

    @Override
    public void onInitialize()
    {
        LOGGER.info("Kits is getting ready...");
    }
}
