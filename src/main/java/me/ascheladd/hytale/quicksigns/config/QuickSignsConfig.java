package me.ascheladd.hytale.quicksigns.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Configuration settings for QuickSigns plugin.
 */
public class QuickSignsConfig {
    
    public static final BuilderCodec<QuickSignsConfig> CODEC = BuilderCodec.builder(QuickSignsConfig.class, QuickSignsConfig::new)
        .append(new KeyedCodec<>("Debug", Codec.BOOLEAN),
            (config, value) -> config.debug = value,
            config -> config.debug)
        .add()
        .build();
    
    private boolean debug = false;
    
    public QuickSignsConfig() {
    }
    
    /**
     * Get debug mode setting.
     */
    public boolean isDebug() {
        return debug;
    }
    
    /**
     * Set debug mode setting.
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
    }
}
