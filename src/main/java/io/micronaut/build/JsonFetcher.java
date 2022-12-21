package io.micronaut.build;

import java.io.IOException;

@FunctionalInterface
public interface JsonFetcher {
    String json(String url) throws IOException;
}
