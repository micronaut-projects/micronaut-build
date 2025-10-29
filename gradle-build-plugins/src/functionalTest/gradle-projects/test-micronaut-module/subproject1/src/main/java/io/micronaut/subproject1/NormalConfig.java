package io.micronaut.subproject1;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties(NormalConfig.PREFIX)
public class NormalConfig {

    public static final String PREFIX = "normal";
    public static final int DEFAULT_VALUE = 42;

    /**
     * @return the config value
     */
    public int getConfig() {
        return DEFAULT_VALUE;
    }

    /**
     * Normal config default value is {@value NormalConfig#DEFAULT_VALUE}.
     *
     * @param config
     */
    public void setConfig(int config) {
        // no-op
    }
}
