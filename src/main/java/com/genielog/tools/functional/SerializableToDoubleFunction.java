package com.genielog.tools.functional;

import java.io.Serializable;
import java.util.function.ToDoubleFunction;

@FunctionalInterface
public interface SerializableToDoubleFunction<T> extends Serializable, ToDoubleFunction<T> {

}
