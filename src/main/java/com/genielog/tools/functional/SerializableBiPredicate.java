package com.genielog.tools.functional;

import java.io.Serializable;
import java.util.function.BiPredicate;

@FunctionalInterface
public interface SerializableBiPredicate<T,R> extends Serializable, BiPredicate<T,R> {

}
