package io.micronaut.subproject1;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties(GenericConfig.PREFIX)
public class GenericConfig<T> {

    public static final String PREFIX = "generic";
    public static final int DEFAULT_VALUE = 234;

    /**
     * @return the config value
     */
    public int getConfig() {
        return DEFAULT_VALUE;
    }

    /**
     * Generic config default value is {@value GenericConfig#DEFAULT_VALUE}.
     *
     * @param config
     */
    public void setConfig(int config) {
        // no-op
    }
}
