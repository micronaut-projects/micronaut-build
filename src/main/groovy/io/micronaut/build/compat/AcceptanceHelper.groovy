package io.micronaut.build.compat

class AcceptanceHelper {
    static String formatAcceptance(String type, String member ) {
        String json = """{
    "type": "$type",
    "member": "$member",
    "reason": "Provide a human readable reason for the change"
}"""
        def changeId = (type + member).replaceAll('[^a-zA-Z0-9]', '_')
        """.
                <br>
                <p>
                If you did this intentionally, please accept the change and provide an explanation:
                <a class="btn btn-info" role="button" data-toggle="collapse" href="#accept-${changeId}" aria-expanded="false" aria-controls="collapseExample">Accept this change</a>
                <div class="collapse" id="accept-${changeId}">
                  <div class="well">
                      In order to accept this change add the following to <code>accepted-api-changes.json</code>:
                    <pre>$json</pre>
                  </div>
                </div>
                </p>""".stripIndent()
    }
}
