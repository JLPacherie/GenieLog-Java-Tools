package com.genielog.tools.functional;

import java.io.Serializable;
import java.util.function.Predicate;

@FunctionalInterface
public interface SerializablePredicate<T> extends Serializable, Predicate<T> {

}
