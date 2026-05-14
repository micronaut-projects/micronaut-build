package io.micronaut.docs.macros;

import org.radeox.macro.BaseMacro;
import org.radeox.macro.parameter.MacroParameter;

import java.io.IOException;
import java.io.Writer;

public class NoteMacro extends BaseMacro {
    @Override
    public String getName() {
        return "note";
    }

    @Override
    public void execute(Writer writer, MacroParameter params) throws IOException {
        writer.append("<blockquote class=\"note\">").append(params.getContent()).append("</blockquote>");
    }
}
