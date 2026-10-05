package demo

class GroovyHelper {
    static List<Integer> lengths(List<String> values) {
        values.collect { it.length() }
    }
}
