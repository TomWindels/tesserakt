package dev.tesserakt.benchmarking.execution

object EndpointUtil {

    fun endpointUrlToEvaluatorName(endpoint: String): String {
        return "endpoint_${endpoint.removePrefix("http://localhost:").replace("/", "%2F")}"
    }

    fun evaluatorNameToEndpointUrl(name: String): String {
        return "http://localhost:${name.removePrefix("endpoint_").replace("%2F", "/")}"
    }

}
