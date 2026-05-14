package io.micronaut.docs.macros;

import io.micronaut.docs.internal.RendererResolver;
import org.radeox.macro.BaseMacro;
import org.radeox.macro.parameter.MacroParameter;

import java.io.IOException;
import java.io.Writer;

public class WarningMacro extends BaseMacro {
    @Override
    public String getName() {
        return "warning";
    }

    @Override
    public void execute(Writer writer, MacroParameter params) throws IOException {
        writer.append(RendererResolver.renderer(params.getContext()).renderWarning(params.getContent()));
    }
}
