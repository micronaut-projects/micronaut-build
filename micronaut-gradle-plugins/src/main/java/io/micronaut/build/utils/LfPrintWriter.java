package io.micronaut.build.utils;

import org.jspecify.annotations.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.Charset;

public class LfPrintWriter extends PrintWriter {

    public LfPrintWriter(OutputStream out) {
        super(out);
    }

    public LfPrintWriter(@NonNull Writer out) {
        super(out);
    }

    public LfPrintWriter(File file, Charset charset) throws IOException {
        super(file, charset);
    }

    @Override
    public void println() {
        print("\n");
    }
}
