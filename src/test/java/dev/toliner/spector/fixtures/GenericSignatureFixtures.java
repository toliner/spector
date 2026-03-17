package dev.toliner.spector.fixtures;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class GenericSignatureFixtures<T extends Number & Comparable<T>> {
    public Map<String, List<? extends Number>> field;

    public <E extends CharSequence> List<E> transform(List<? super T> input) throws IOException {
        return null;
    }

    public class Inner {
        public Inner(List<T> values) {
        }
    }
}
