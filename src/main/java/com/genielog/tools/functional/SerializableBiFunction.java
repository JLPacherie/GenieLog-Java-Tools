package com.genielog.tools.functional;

import java.io.Serializable;
import java.util.function.BiFunction;

@FunctionalInterface
public interface SerializableBiFunction<I,T,O> extends Serializable, BiFunction<I,T,O> {

}
