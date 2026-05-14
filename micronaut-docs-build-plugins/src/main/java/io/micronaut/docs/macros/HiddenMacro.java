package io.micronaut.docs.macros;

import org.radeox.macro.BaseMacro;
import org.radeox.macro.parameter.MacroParameter;

import java.io.IOException;
import java.io.Writer;

public class HiddenMacro extends BaseMacro {
    @Override
    public String getName() {
        return "hidden";
    }

    @Override
    public void execute(Writer out, MacroParameter params) throws IOException {
        out.append("<div class=\"hidden-block\">").append(params.getContent()).append("</div>");
    }
}
