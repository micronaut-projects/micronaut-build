package io.micronaut.build.compat;

final class AcceptanceHelper {
    private AcceptanceHelper() {
    }

    static String formatAcceptance(String type, String member) {
        String json = """
            {
                "type": "%s",
                "member": "%s",
                "reason": "Provide a human readable reason for the change"
            }""".formatted(type, member);
        String changeId = (type + member).replaceAll("[^a-zA-Z0-9]", "_");
        return """
            .
            <br>
            <p>
            If you did this intentionally, please accept the change and provide an explanation:
            <a class="btn btn-info" role="button" data-toggle="collapse" href="#accept-%s" aria-expanded="false" aria-controls="collapseExample">Accept this change</a>
            <div class="collapse" id="accept-%s">
              <div class="well">
                  In order to accept this change add the following to <code>accepted-api-changes.json</code>:
                <pre>%s</pre>
              </div>
            </div>
            </p>""".formatted(changeId, changeId, json);
    }
}
