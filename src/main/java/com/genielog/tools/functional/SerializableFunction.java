package com.genielog.tools.functional;

import java.io.Serializable;
import java.util.function.Function;

@FunctionalInterface
public interface SerializableFunction<I,O> extends Serializable, Function<I,O> {

}
